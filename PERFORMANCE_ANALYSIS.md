# Snyk IntelliJ Plugin - Performance Analysis

## Executive Summary

This document presents a detailed performance analysis of the Snyk IntelliJ Plugin, identifying potential bottlenecks and areas for optimization while maintaining current functionality.

---

## 1. Architecture Overview

The plugin follows a Language Server Protocol (LSP) based architecture with the following key components:

- **LanguageServerWrapper**: Manages LSP communication with the Snyk CLI
- **SnykLanguageClient**: Handles incoming LSP notifications and requests
- **SnykCachedResults**: In-memory cache for scan results
- **SnykToolWindowPanel**: Main UI component for displaying results
- **SnykAnnotator**: Editor annotations for inline issue display

---

## 2. Identified Performance Issues

### 2.1 Memory & Object Allocation

#### 2.1.1 Repeated Gson Instance Creation
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/SnykLanguageClient.kt:145`
```kotlin
val issue = Gson().fromJson(it.data.toString(), ScanIssue::class.java)
```

**Issue**: Creates a new `Gson` instance for each diagnostic, which is expensive. With hundreds of issues, this creates unnecessary GC pressure.

**Recommendation**: 
- Reuse a shared `Gson` instance (already exists as `gson` field in the class)
- Use `this.gson.fromJson()` instead of `Gson().fromJson()`

#### 2.1.2 ScanIssue Initialization Overhead
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/Types.kt:163-168`
```kotlin
init {
    virtualFile = filePath.toVirtualFile()
    document = virtualFile?.getDocument()
    startOffset = document?.getLineStartOffset(range.start.line)?.plus(range.start.character)
    endOffset = document?.getLineStartOffset(range.end.line)?.plus(range.end.character)
}
```

**Issue**: Every `ScanIssue` eagerly computes file/document/offset info at construction time, even if the issue is never displayed.

**Recommendation**: 
- Make these properties truly lazy (remove init block, rely on lazy getters already defined)
- Consider caching document lookups per file path

#### 2.1.3 Repeated Collection Flattening
**Location**: Multiple files including `SnykToolWindowScanListener.kt`

**Issue**: Multiple calls to `.flatten()` on the same data structures in quick succession:
```kotlin
val flattenedResults = snykResults.values.flatten()
// Later...
flattenedResults.filter { ... }.distinct().size
flattenedResults.count { ... }
```

**Recommendation**: Cache flattened results when processing scan results instead of repeatedly flattening.

---

### 2.2 Threading & Synchronization

#### 2.2.1 Blocking Thread.sleep in Language Server
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:427-428`, `478`
```kotlin
Thread.sleep(5000) // Feature flag retry
Thread.sleep(1000) // Restart delay
```

**Issue**: Blocking sleeps in background threads waste resources and delay operations.

**Recommendation**: 
- Use `ScheduledExecutorService` or coroutines for delays
- Consider exponential backoff for retries

#### 2.2.2 CLI Download Polling Loop
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/services/SnykTaskQueueService.kt:70-75`
```kotlin
fun waitUntilCliDownloadedIfNeeded() {
    downloadLatestRelease()
    do {
        Thread.sleep(WAIT_FOR_DOWNLOAD_MILLIS)
    } while (isCliDownloading())
}
```

**Issue**: Busy-waiting loop with 1-second sleeps. Blocks the calling thread unnecessarily.

**Recommendation**: 
- Use `CompletableFuture` or callbacks for download completion
- Replace polling with event-driven approach

#### 2.2.3 Synchronous LSP Command Execution
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:451-452`
```kotlin
private fun executeCommand(param: ExecuteCommandParams, timeoutMillis: Long = 5000): Any? =
    languageServer.workspaceService.executeCommand(param).get(timeoutMillis, TimeUnit.MILLISECONDS)
```

**Issue**: Many LSP commands block with `get()`, potentially freezing UI if called from EDT.

**Recommendation**: 
- Use async handlers with `thenAccept` or `whenComplete`
- Ensure all LSP calls are on background threads

---

### 2.3 UI Performance

#### 2.3.1 Tree Expansion Performance
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/toolwindow/TreeNodeExpander.kt`

**Positive**: The `TreeNodeExpander` already implements chunked expansion with EDT yields. This is good.

**Potential Improvement**: The `MAX_AUTO_EXPAND_NODES = 50` limit may be too low for large projects.

#### 2.3.2 Debouncing Already Implemented
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/toolwindow/SnykToolWindowPanel.kt:121-132`
```kotlin
private val reloadAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
private const val RELOAD_DEBOUNCE_MS = 100
```

**Positive**: Debouncing is properly implemented for tree reloads.

#### 2.3.3 Annotation Refresh Threshold
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/Utils.kt:248-274`
```kotlin
private const val SINGLE_FILE_DECORATION_UPDATE_THRESHOLD = 5

if (openFiles.size > SINGLE_FILE_DECORATION_UPDATE_THRESHOLD) {
    DaemonCodeAnalyzer.getInstance(project).restart()
} else {
    openFiles.forEach { refreshAnnotationsForFile(project, it) }
}
```

**Issue**: Threshold of 5 is arbitrary. Full restart for >5 files may be slower than individual refreshes.

**Recommendation**: 
- Profile and tune this threshold
- Consider lazy/batch annotation updates

#### 2.3.4 SnykToolWindowScanListener Multiple Redraws
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/toolwindow/SnykToolWindowScanListener.kt`

**Issue**: When scan finishes, multiple methods are called that each trigger tree updates:
- `displaySnykCodeResults()` → `displayIssues()` → `smartReloadRootNode()`
- Each product scan completion triggers separate UI updates

**Recommendation**: 
- Batch UI updates when multiple products finish scanning close together
- Use a coalescing mechanism similar to debouncing

---

### 2.4 File System & I/O

#### 2.4.1 Debounce Cache for File Changes
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/LanguageServerBulkFileListener.kt:73-75`
```kotlin
private val debounceFileCache =
    CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMillis(1000)).build<String, Boolean>()
```

**Positive**: Good debouncing implementation for file changes.

#### 2.4.2 File Content Reading on Save
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/LanguageServerBulkFileListener.kt:60-65`
```kotlin
val param = DidSaveTextDocumentParams(
    TextDocumentIdentifier(virtualFile.toLanguageServerURI()),
    virtualFile.readText()  // <-- Reads entire file
)
```

**Issue**: Reads entire file content when sending didSave. For large files, this is expensive.

**Recommendation**: 
- Consider if file content is actually needed (LSP 3.16+ allows omitting it)
- Stream content if needed rather than loading entire file

#### 2.4.3 VirtualFile to URI Conversion
**Location**: Multiple locations

**Issue**: Repeated `toLanguageServerURI()`, `toVirtualFile()`, `fromUriToPath()` conversions.

**Recommendation**: Cache URI conversions where files are accessed repeatedly.

---

### 2.5 LSP Communication

#### 2.5.1 Synchronous Initialization
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:307`
```kotlin
initializeResult = languageServer.initialize(params).get(INITIALIZATION_TIMEOUT, TimeUnit.SECONDS)
```

**Issue**: 20-second blocking wait during initialization.

**Recommendation**: 
- Make initialization async with callbacks
- Show UI feedback during initialization

#### 2.5.2 Code Action Timeout
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/annotator/SnykAnnotator.kt:51`
```kotlin
private const val CODEACTION_TIMEOUT = 10L
```

**Issue**: 10-second timeout per code action request. With many issues, this can accumulate.

**Recommendation**: 
- Batch code action requests
- Cache code actions per range
- Consider reducing timeout and retrying selectively

#### 2.5.3 ExecutorService Thread Pool
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:97`
```kotlin
private val executorService: ExecutorService = Executors.newCachedThreadPool()
```

**Issue**: Cached thread pool can grow unbounded.

**Recommendation**: 
- Use bounded thread pool: `Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())`
- Or use IntelliJ's built-in `ApplicationManager.getApplication().executeOnPooledThread()`

---

### 2.6 Cache Management

#### 2.6.1 Cache Structure
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/SnykCachedResults.kt:40-42`
```kotlin
val currentSnykCodeResultsLS: MutableMap<SnykFile, Set<ScanIssue>> = ConcurrentMap()
val currentOSSResultsLS: ConcurrentMap<SnykFile, Set<ScanIssue>> = ConcurrentMap()
val currentIacResultsLS: MutableMap<SnykFile, Set<ScanIssue>> = ConcurrentMap()
```

**Positive**: Using `ConcurrentMap` for thread safety.

**Issue**: No size limits - large projects can accumulate significant memory.

**Recommendation**: 
- Add eviction policy for stale entries
- Consider weak references for `SnykFile` keys
- Add memory pressure monitoring

#### 2.6.2 Missing Issue Description Caching
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/Types.kt:296-302`
```kotlin
private fun getHtml(details: String?, project: Project): String {
    return if (details.isNullOrEmpty() && this.id.isNotBlank()) {
        LanguageServerWrapper.getInstance(project).generateIssueDescription(this) ?: ""
    } else { "" }
}
```

**Issue**: Issue descriptions are fetched on-demand without caching.

**Recommendation**: Cache generated descriptions to avoid repeated LSP calls.

---

### 2.7 Message Bus Usage

#### 2.7.1 Multiple Listeners for Same Event
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/toolwindow/SnykToolWindowPanel.kt:189-237`

**Issue**: Multiple subscriptions to `SNYK_SCAN_TOPIC` from the same component:
```kotlin
project.messageBus.connect(this).subscribe(SnykScanListener.SNYK_SCAN_TOPIC, scanListener)
// ...
project.messageBus.connect(this).subscribe(SnykScanListener.SNYK_SCAN_TOPIC, object : SnykScanListener { ... })
```

**Recommendation**: Consolidate listeners to reduce event dispatch overhead.

---

## 3. Positive Patterns Already Implemented

1. **Progressive Tree Expansion**: `TreeNodeExpander` implements chunked expansion ✓
2. **Debouncing**: File listener and tree reload debouncing ✓
3. **Disposed Checks**: Consistent `isDisposed` checks throughout ✓
4. **Background Tasks**: Using `runAsync` and `runInBackground` ✓
5. **Concurrent Collections**: Using `ConcurrentMap` for caches ✓
6. **Read Actions**: Proper use of `ReadAction.run` for PSI access ✓

---

## 4. Priority Recommendations

### High Priority (Immediate Impact)
1. **Reuse Gson instance** in `SnykLanguageClient.getScanIssues()` - Easy fix, immediate benefit
2. **Remove eager initialization** in `ScanIssue.init` block - Reduces memory per issue
3. **Replace polling with events** in `waitUntilCliDownloadedIfNeeded()` - Frees blocked threads

### Medium Priority (Moderate Effort)
4. **Batch UI updates** when multiple scan products complete - Smoother UI
5. **Add cache eviction** for large projects - Prevents memory growth
6. **Cache issue descriptions** - Reduces LSP round-trips
7. **Use bounded thread pool** for LSP executor - Prevents resource exhaustion

### Low Priority (Optimization)
8. **Tune annotation refresh threshold** - Profile-guided optimization
9. **Batch code action requests** - Reduces annotation latency
10. **Cache URI/VirtualFile conversions** - Micro-optimization

---

## 5. Profiling Recommendations

To validate these findings and identify additional issues:

1. **Memory Profiling**: Use IntelliJ's built-in memory profiler or YourKit to:
   - Track `ScanIssue` instance counts
   - Monitor cache growth patterns
   - Identify memory leaks

2. **Thread Analysis**: 
   - Capture thread dumps during scan operations
   - Identify thread contention points
   - Verify background thread usage

3. **UI Responsiveness**:
   - Use IntelliJ's "Report UI Freezes" feature
   - Profile tree expansion with large issue counts
   - Measure annotation application time

4. **LSP Communication**:
   - Log request/response times
   - Identify slow commands
   - Monitor connection health

---

## 6. Benchmarking Suggestions

Create performance tests that measure:
- Time to display 100, 500, 1000+ issues
- Memory usage per 100 issues
- Tree expansion time for deep hierarchies
- Annotation refresh time for large files
- LSP command response times under load

---

## 7. Conclusion

The Snyk IntelliJ Plugin has a solid architecture with several good performance patterns already in place. The main opportunities for improvement are:

1. **Object allocation reduction** (Gson reuse, lazy initialization)
2. **Threading model improvements** (async over blocking)
3. **UI update batching** (coalescing rapid changes)
4. **Cache management** (eviction, bounded growth)

These improvements can be implemented incrementally without architectural changes, maintaining full backward compatibility with current functionality.

---

## 8. Event Dispatch Thread (EDT) Deep Analysis

This section provides a detailed analysis of EDT usage patterns, identifying potential UI freezes and optimization opportunities while maintaining correct program flow.

### 8.1 EDT Usage Patterns Overview

The plugin uses several mechanisms for EDT interactions:
- `invokeLater` / `ApplicationManager.getApplication().invokeLater` - Queues work for EDT
- `runAsync` - Executes on a pooled background thread
- `Alarm` - Scheduled/debounced execution
- `executeOnPooledThread` - Background thread execution
- `runInBackground` - Background task with progress indicator

### 8.2 Critical EDT Issues

#### 8.2.1 Recursive Tree Expansion Flooding EDT Queue
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/UIUtils.kt:350-357`
```kotlin
fun expandTreeNodeRecursively(tree: JTree, node: DefaultMutableTreeNode) {
    invokeLater {
        tree.expandPath(TreePath(node.path))
    }
    node.children().asSequence().forEach {
        expandTreeNodeRecursively(tree, it as DefaultMutableTreeNode)
    }
}
```

**Issue**: This function queues a **separate `invokeLater` for EVERY tree node**. For a tree with 500 issues across 50 files, this queues 550+ EDT tasks. This can:
- Flood the EDT queue
- Cause stuttering UI updates
- Block other UI operations

**Recommendation**: Replace with batched expansion using `TreeUtil.promiseExpand()` or chunk expansion like `TreeNodeExpander` already does:
```kotlin
fun expandTreeNodeRecursively(tree: JTree, node: DefaultMutableTreeNode) {
    invokeLater {
        // Collect all paths first, then expand in one EDT pass
        val paths = mutableListOf<TreePath>()
        collectPaths(node, paths)
        paths.forEach { tree.expandPath(it) }
    }
}
```

#### 8.2.2 Description Panel Creation on EDT (via runAsync callback)
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/toolwindow/SnykToolWindowPanel.kt:183-187`
```kotlin
vulnerabilitiesTree.selectionModel.addTreeSelectionListener { treeSelectionEvent ->
    runAsync {
        updateDescriptionPanelBySelectedTreeNode(treeSelectionEvent)
    }
}
```

Then at lines 360-373, `getDescriptionPanel()` creates a `SuggestionDescriptionPanel`:
```kotlin
val newDescriptionPanel = selectedNode.getDescriptionPanel()  // Heavy operation!
descriptionPanel.removeAll()
descriptionPanel.add(newDescriptionPanel, BorderLayout.CENTER)
```

**Issue**: `SuggestionDescriptionPanel` constructor (in `JCEFDescriptionPanel.kt`) does:
1. Creates JCEF browser instance (expensive)
2. Calls `issue.details(project)` which may invoke LSP `generateIssueDescription`
3. Sets up multiple load handlers
4. Performs HTML formatting

Although `updateDescriptionPanelBySelectedTreeNode` runs via `runAsync`, the subsequent `invokeLater` at line 392-395 for `revalidate()`/`repaint()` still involves heavy UI work.

**Recommendation**:
- Cache `SuggestionDescriptionPanel` instances per issue ID
- Pre-create panels for visible/selected issues
- Consider lazy JCEF initialization

#### 8.2.3 Multiple invokeLater Callbacks Without Coalescing
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/toolwindow/SnykToolWindowPanel.kt:227-230`
```kotlin
invokeLater {
    vulnerabilitiesTree.isRootVisible = pluginSettings().isDeltaFindingsEnabled()
}
```

This is called from `onPublishDiagnostics` which fires for **each file**. With 100 files, this queues 100 EDT tasks just to set the same boolean property.

**Recommendation**: Debounce or consolidate:
```kotlin
private var pendingRootVisibilityUpdate = AtomicBoolean(false)

override fun onPublishDiagnostics(...) {
    // ... cache update ...
    if (!pendingRootVisibilityUpdate.getAndSet(true)) {
        invokeLater {
            pendingRootVisibilityUpdate.set(false)
            vulnerabilitiesTree.isRootVisible = pluginSettings().isDeltaFindingsEnabled()
        }
    }
}
```

#### 8.2.4 Scan Finished Handlers Not Coalesced
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/toolwindow/SnykToolWindowScanListener.kt:79-106`

When scans finish, each product triggers separate handlers:
- `scanningSnykCodeFinished()` → `displaySnykCodeResults()` → `smartReloadRootNode()`
- `scanningOssFinished()` → `displayOssResults()` → `smartReloadRootNode()`
- `scanningIacFinished()` → `displayIacResults()` → `smartReloadRootNode()`

Each calls `refreshAnnotationsForOpenFiles(project)` which queues more EDT work.

**Issue**: If scans finish close together (common case), multiple UI rebuilds happen in sequence.

**Recommendation**: Add scan completion coalescing:
```kotlin
private val scanCompletionAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
private val pendingProducts = mutableSetOf<LsProduct>()

private fun onScanFinished(product: LsProduct) {
    synchronized(pendingProducts) {
        pendingProducts.add(product)
    }
    scanCompletionAlarm.cancelAllRequests()
    scanCompletionAlarm.addRequest({
        val products = synchronized(pendingProducts) {
            pendingProducts.toSet().also { pendingProducts.clear() }
        }
        // Single batched UI update for all finished products
        displayAllResults(products)
    }, 100)
}
```

### 8.3 EDT Flow Analysis

#### 8.3.1 Diagnostic Publishing Flow
```
LSP Thread                    Background Thread              EDT
    |                               |                         |
publishDiagnostics()                |                         |
    |---> updateCache()             |                         |
    |         |                     |                         |
    |         |---> getScanIssues() (creates issues)          |
    |         |                     |                         |
    |---> scanPublisher.onPublishDiagnostics()                |
              |                     |                         |
              |--------- invokeLater -----------------------> |
                                    |                  isRootVisible=
                                    |                         |
              (repeat per file)     |                         |
```

**Issue**: Each file publishes diagnostics → triggers EDT update. N files = N EDT tasks.

#### 8.3.2 Tree Selection Flow
```
EDT (selection event)          Background Thread              EDT
    |                               |                         |
selectionListener()                 |                         |
    |---> runAsync ---------------> |                         |
                   updateDescriptionPanel()                   |
                              |                               |
                              |---> invokeLater ------------> |
                                               descriptionPanel.revalidate()
                                               descriptionPanel.repaint()
```

**Good Pattern**: Selection handling correctly moves work off EDT.

#### 8.3.3 Annotation Refresh Flow
**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/Utils.kt:250-274`
```kotlin
fun refreshAnnotationsForOpenFiles(project: Project) {
    if (project.isDisposed || ApplicationManager.getApplication().isDisposed) return
    runAsync {  // ✓ Good: starts on background thread
        VirtualFileManager.getInstance().asyncRefresh()
        
        val openFiles = FileEditorManager.getInstance(project).openFiles
        
        ApplicationManager.getApplication().invokeLater {  // EDT for CodeVision
            if (!project.isDisposed) {
                project.service<CodeVisionHost>().invalidateProvider(...)
            }
        }
        
        if (openFiles.size > SINGLE_FILE_DECORATION_UPDATE_THRESHOLD) {
            invokeLater {  // EDT for restart
                if (!project.isDisposed) {
                    DaemonCodeAnalyzer.getInstance(project).restart()
                }
            }
        } else {
            openFiles.forEach { openFile ->  // N calls to refreshAnnotationsForFile
                // Each refreshAnnotationsForFile does invokeLater internally!
            }
        }
    }
}
```

**Issue**: When `openFiles.size <= 5`, this queues 5 separate `invokeLater` calls (one per file via `refreshAnnotationsForFile`).

**Recommendation**: Batch the file refreshes:
```kotlin
if (openFiles.size > SINGLE_FILE_DECORATION_UPDATE_THRESHOLD) {
    invokeLater {
        if (!project.isDisposed) {
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }
} else {
    invokeLater {
        if (!project.isDisposed) {
            val analyzer = DaemonCodeAnalyzer.getInstance(project)
            openFiles.forEach { openFile ->
                findPsiFile(project, openFile)?.let { psiFile ->
                    analyzer.restart(psiFile)
                }
            }
        }
    }
}
```

### 8.4 ModalityState Analysis

**Locations using `ModalityState.any()`**:
- `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/services/SnykCliAuthenticationService.kt:70, 90, 97`
- `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/io/snyk/plugin/ui/settings/HTMLSettingsPanel.kt:83, 226`

**Good Usage**: `ModalityState.any()` is correctly used for callbacks that must run even when modal dialogs are open (authentication dialogs, settings panels).

**Missing ModalityState**: Some `invokeLater` calls don't specify modality, which means they won't run if a modal dialog is open. This is usually fine but can cause issues during auth flows.

### 8.5 Alarm Usage Review

| Location | Thread | Purpose | Assessment |
|----------|--------|---------|------------|
| `Utils.kt:181-194` | Default | Process cancellation check | ✓ OK |
| `UIUtils.kt:314-329` | Default | Scroll panel to top | Could use SWING_THREAD |
| `SnykSettingsDialog.kt:94` | POOLED_THREAD | Background tasks in dialogs | ✓ Good workaround |
| `SnykToolWindowPanel.kt:121` | SWING_THREAD | Tree reload debouncing | ✓ Correct |

### 8.6 Thread.sleep on Pooled Threads

**Location**: `@/Users/bdoetsch/workspace/snyk-intellij-plugin/src/main/kotlin/snyk/common/lsp/SnykLanguageClient.kt:337-339`
```kotlin
ApplicationManager.getApplication().executeOnPooledThread {
    Thread.sleep(30000)  // 30 seconds!
    notification.expire()
}
```

**Issue**: Holds a pooled thread hostage for 30 seconds just to expire a notification.

**Recommendation**: Use `Alarm`:
```kotlin
private val notificationAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

// In showInfo handler:
notificationAlarm.addRequest({ notification.expire() }, 30000)
```

### 8.7 Summary: EDT Optimization Priorities

| Priority | Issue | Impact | Fix Complexity |
|----------|-------|--------|----------------|
| **High** | Recursive tree expansion flooding EDT | Severe UI stutter | Easy - batch calls |
| **High** | Per-file `invokeLater` in `onPublishDiagnostics` | N EDT tasks per scan | Medium - debounce |
| **Medium** | Multiple scan completion handlers | Redundant UI rebuilds | Medium - coalesce |
| **Medium** | Individual file annotation refresh | N EDT tasks | Easy - batch |
| **Medium** | Description panel creation overhead | Selection lag | Medium - caching |
| **Low** | Thread.sleep for notification expiry | Wasted thread | Easy - use Alarm |

### 8.8 Recommended EDT Architecture

```
                     ┌─────────────────────────────────────────────┐
                     │              EDT (Swing Thread)              │
                     │  - Tree revalidation (batched)               │
                     │  - Panel updates (debounced)                 │
                     │  - Selection handling (immediate)            │
                     └─────────────────────────────────────────────┘
                                        ▲
                                        │ invokeLater (coalesced)
                     ┌─────────────────────────────────────────────┐
                     │           Coalescing/Debounce Layer          │
                     │  - Alarm for tree reloads (already exists)   │
                     │  - Alarm for annotation refresh (add)        │
                     │  - Scan completion coalescer (add)           │
                     └─────────────────────────────────────────────┘
                                        ▲
                                        │ runAsync / executeOnPooledThread
                     ┌─────────────────────────────────────────────┐
                     │            Background Threads                │
                     │  - LSP communication                         │
                     │  - Issue processing                          │
                     │  - File operations                           │
                     └─────────────────────────────────────────────┘
```

---

*Analysis Date: December 2024*
*Plugin Version: Based on current source code review*
