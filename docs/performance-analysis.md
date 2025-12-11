# Performance Analysis: Snyk IntelliJ Plugin

## Executive Summary

This document identifies critical patterns that can freeze the IDE or cause UI unresponsiveness. The main issues involve blocking operations on the Event Dispatch Thread (EDT), synchronous waits with network I/O, and improper threading patterns.

---

## Critical Issues Found

### 1. Blocking `invokeAndWait` on Background/Scan Threads

**Location:** `src/main/kotlin/io/snyk/plugin/services/SnykTaskQueueService.kt:57-59`
```kotlin
ApplicationManager.getApplication().invokeAndWait {
    FileDocumentManager.getInstance().saveAllDocuments()
}
```
**Problem:** Called from `runInBackground`, this blocks the background thread waiting for EDT. If EDT is blocked, this creates a deadlock scenario.

**Location:** `src/main/kotlin/io/snyk/plugin/ui/toolwindow/SnykToolWindowPanel.kt:717-718`
```kotlin
ApplicationManager.getApplication().invokeAndWait {
    (vulnerabilitiesTree.model as DefaultTreeModel).reload(nodeToReload)
}
```
**Problem:** Called during scan result processing, can block background threads.

**Location:** `src/main/kotlin/io/snyk/plugin/ui/toolwindow/SnykToolWindowScanListener.kt:251-255`
```kotlin
ApplicationManager.getApplication().invokeAndWait {
    userObjectsForExpandedChildren =
        snykToolWindowPanel.userObjectsForExpandedNodes(rootNode)
    selectedNodeUserObject = TreeUtil.findObjectInPath(vulnerabilitiesTree.selectionPath, Any::class.java)
}
```

**Location:** `src/main/kotlin/snyk/trust/TrustedProjects.kt:61-76`
```kotlin
invokeAndWaitIfNeeded {
    val result = MessageDialogBuilder...
}
```
**Problem:** Shows a modal dialog using `invokeAndWaitIfNeeded`, blocking callers.

---

### 2. Synchronous `CompletableFuture.get()` Blocking Calls

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:307`
```kotlin
initializeResult = languageServer.initialize(params).get(INITIALIZATION_TIMEOUT, TimeUnit.SECONDS)
```

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:235`
```kotlin
languageServer.shutdown().get(1, TimeUnit.SECONDS)
```

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:451-452`
```kotlin
languageServer.workspaceService.executeCommand(param).get(timeoutMillis, TimeUnit.MILLISECONDS)
```

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:548`
```kotlin
languageServer.workspaceService.executeCommand(cmd).get(5, TimeUnit.SECONDS)
```

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:587`
```kotlin
.executeCommand(authLinkCmd).get(10, TimeUnit.SECONDS)
```

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:755`
```kotlin
languageServer.workspaceService.executeCommand(executeCommandParams).get(timeoutMs, TimeUnit.MILLISECONDS)
```

**Location:** `src/main/kotlin/snyk/common/annotator/SnykAnnotator.kt:225-226`
```kotlin
languageServer.textDocumentService
    .codeAction(params).get(CODEACTION_TIMEOUT, TimeUnit.SECONDS)
```
**Problem:** This runs in the annotator which can block highlighting.

---

### 3. `Thread.sleep()` on Potentially Critical Threads

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:427-428`
```kotlin
Thread.sleep(5000)
executeCommand(param, 30000)
```
**Problem:** 5-second sleep on retry, potentially on EDT or critical background thread.

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:478`
```kotlin
Thread.sleep(1000)
```

**Location:** `src/main/kotlin/io/snyk/plugin/services/SnykTaskQueueService.kt:72-74`
```kotlin
do {
    Thread.sleep(WAIT_FOR_DOWNLOAD_MILLIS)
} while (isCliDownloading())
```
**Problem:** Busy-wait loop polling every second.

**Location:** `src/main/kotlin/snyk/common/lsp/SnykLanguageClient.kt:338`
```kotlin
Thread.sleep(30000)
```
**Problem:** 30-second sleep to expire notification (on pooled thread, but wasteful).

**Location:** `src/main/kotlin/io/snyk/plugin/ui/settings/HTMLSettingsPanel.kt:119`
```kotlin
Thread.sleep(1000)
```

---

### 4. Lock Contention Issues

**Location:** `src/main/kotlin/snyk/common/lsp/LanguageServerWrapper.kt:136, 362-380`
```kotlin
var isInitializing: ReentrantLock = ReentrantLock()
...
try {
    isInitializing.lock()
    ...
} finally {
    isInitializing.unlock()
}
```
**Problem:** `ensureLanguageServerInitialized()` uses a lock that can block multiple threads trying to access LS simultaneously.

---

### 5. Synchronous Operations During Plugin Startup

**Location:** `src/main/kotlin/io/snyk/plugin/SnykPostStartupActivity.kt:51`
```kotlin
getSnykTaskQueueService(project)?.waitUntilCliDownloadedIfNeeded()
```
**Problem:** Called during startup, potentially blocking project opening.

---

### 6. EDT-Sensitive Operations in Message Handlers

**Location:** `src/main/kotlin/snyk/common/lsp/SnykLanguageClient.kt:373`
```kotlin
val messageActionItem = showMessageRequestFutures.poll(10, TimeUnit.SECONDS)
```
**Problem:** 10-second blocking poll in `showMessageRequest`.

---

## Recommendations

### High Priority Fixes

#### 1. Replace `invokeAndWait` with `invokeLater` Where Possible
```kotlin
// Before:
ApplicationManager.getApplication().invokeAndWait {
    (vulnerabilitiesTree.model as DefaultTreeModel).reload(nodeToReload)
}

// After: 
invokeLater {
    (vulnerabilitiesTree.model as DefaultTreeModel).reload(nodeToReload)
}
```

#### 2. Move Blocking LS Calls Off EDT with `runAsync` + Callbacks
```kotlin
// Before:
fun getAuthenticatedUser(): String? {
    val result = languageServer.workspaceService.executeCommand(cmd).get(5, TimeUnit.SECONDS)
    ...
}

// After:
fun getAuthenticatedUserAsync(callback: (String?) -> Unit) {
    runAsync {
        try {
            val result = languageServer.workspaceService.executeCommand(cmd).get(5, TimeUnit.SECONDS)
            callback(result?.get("username") as? String)
        } catch (e: Exception) {
            callback(null)
        }
    }
}
```

#### 3. Replace Busy-Wait Loops with Event-Based Notification
```kotlin
// Before:
do {
    Thread.sleep(WAIT_FOR_DOWNLOAD_MILLIS)
} while (isCliDownloading())

// After: Use a CountDownLatch or CompletableFuture
private val cliDownloadComplete = CompletableFuture<Unit>()

fun onCliDownloadComplete() {
    cliDownloadComplete.complete(Unit)
}

suspend fun waitUntilCliDownloaded() {
    cliDownloadComplete.await()
}
```

#### 4. Use `ProgressManager.checkCanceled()` in Long Operations
Ensure all long-running loops check for cancellation:
```kotlin
while (condition) {
    ProgressManager.checkCanceled()
    // work
}
```

#### 5. Make LS Initialization Truly Async
Consider using a state machine pattern:
```kotlin
enum class LsState { NOT_INITIALIZED, INITIALIZING, READY, ERROR }

private val lsState = AtomicReference(LsState.NOT_INITIALIZED)
private val readyCallbacks = ConcurrentLinkedQueue<() -> Unit>()

fun whenReady(callback: () -> Unit) {
    when (lsState.get()) {
        LsState.READY -> callback()
        else -> {
            readyCallbacks.add(callback)
            ensureInitializing()
        }
    }
}
```

#### 6. Defer Non-Essential Startup Work
```kotlin
// Instead of blocking startup:
override suspend fun execute(project: Project) {
    // Only register listeners synchronously
    registerListeners(project)
    
    // Defer everything else
    runAsync {
        getSnykTaskQueueService(project)?.downloadLatestRelease()
        // etc.
    }
}
```

#### 7. Use Read/Write Actions Properly
Replace direct `runReadAction` in hot paths with `ReadAction.nonBlocking()`:
```kotlin
ReadAction.nonBlocking {
    ProjectFileIndex.getInstance(project).isInContent(vf)
}.inSmartMode(project).submit(AppExecutorUtil.getAppExecutorService())
```

---

### Medium Priority Fixes

| Issue | Location | Fix |
|-------|----------|-----|
| Annotator blocking on code actions | `SnykAnnotator.kt:225` | Cache code actions, fetch async |
| Trust dialog blocks caller | `TrustedProjects.kt:61` | Return immediately, use callback |
| `synchronized` collections | `LanguageServerWrapper.kt:105` | Use `ConcurrentHashMap.newKeySet()` |
| Tree selection listener does work | `SnykToolWindowPanel.kt:152` | Debounce & cancel previous work |

---

### Architectural Improvements

1. **Introduce a CoroutineScope per Project** for structured concurrency
2. **Create an `LsCommandExecutor`** service that queues and executes LS commands off-EDT
3. **Add EDT assertions** in debug mode to catch accidental EDT blocking
4. **Implement request coalescing** for frequent operations (e.g., config updates)
5. **Add performance metrics** using `com.intellij.diagnostic.PerformanceWatcher`

---

## Quick Win Summary

| Fix | Impact | Effort |
|-----|--------|--------|
| Replace `invokeAndWait` → `invokeLater` | High | Low |
| Move `Thread.sleep` to scheduled executor | High | Low |
| Add timeout guards to all `.get()` calls | Medium | Low |
| Make startup non-blocking | High | Medium |
| Cache code actions in annotator | Medium | Medium |
| Event-based CLI download waiting | Medium | Medium |

---

## Implementation Priority

### Phase 1: Quick Wins (1-2 days)
- Replace all `invokeAndWait` with `invokeLater` where return value is not needed
- Remove or replace `Thread.sleep()` calls with proper scheduling
- Add proper timeout handling to prevent infinite waits

### Phase 2: Core Fixes (3-5 days)
- Refactor `LanguageServerWrapper.ensureLanguageServerInitialized()` to be async
- Replace busy-wait CLI download loop with event-based approach
- Fix startup sequence to be non-blocking

### Phase 3: Architecture (1-2 weeks)
- Introduce coroutine-based command executor for LS
- Add comprehensive EDT assertions in debug builds
- Implement request coalescing for frequent operations

---

## Testing Recommendations

1. **Use IntelliJ's built-in freeze detector** - Enable in Registry: `ide.freeze.reporter.enabled`
2. **Add performance tests** measuring time on EDT
3. **Use async stack traces** to identify blocking call chains
4. **Monitor with VisualVM** during typical user workflows

---

*Document generated: December 2024*
