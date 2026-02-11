package io.snyk.plugin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent

/**
 * For our caches we need following events for file: Create, Change, Delete Also in our caches we
 * need next type of actions: _add_ _update_ _clean_ Note: Current implementation meaning: add -
 * mark results as invalid anymore (i.e. next scan attempt should run scan and not use cache)
 * update - mark item(file) result as obsolete (i.e. next scan attempt should run scan and not use
 * cache) clean - mark item(file) result as obsolete (i.e. next scan attempt should run scan and not
 * use cache)
 *
 * BulkFileListener provide next events: Create, ContentChange, Move, Copy, Delete Also in `before`
 * state we have access to old files. While in `after` state we have access for new/updated files
 * too (but old might not exist anymore)
 *
 * Next mapping/interpretation for BulkFileListener type of events should be used:
 *
 * Create
 * - addressed at `after` state, new file processed to _add_ caches
 *
 * ContentChange
 * - addressed at `before` state, old file processed to _clean_ caches
 * - addressed at `after` state, new file processed to _update_ caches
 *
 * Move
 * - addressed at `before` state, old file processed to _clean_ caches
 * - addressed at `after` state, new file processed to _add_ caches
 *
 * Rename
 * - addressed at `before` state, old file processed to _clean_ caches
 * - addressed at `after` state, new file processed to _add_ caches
 *
 * Copy
 * - addressed at `after` state, new file processed to _add_ caches
 *
 * Delete
 * - addressed at `before` state, old file processed to _clean_ caches
 */
abstract class SnykBulkFileListener(val project: Project) : BulkFileListener {
  /** **************************** Before ************************* */
  override fun before(events: List<VFileEvent>) {
    if (!isFileListenerEnabled() || project.isDisposed) return
    before(project, getBeforeVirtualFiles(events))
  }

  abstract fun before(project: Project, virtualFilesAffected: Set<VirtualFile>)

  /** **************************** After ************************* */
  override fun after(events: MutableList<out VFileEvent>) {
    if (!isFileListenerEnabled() || project.isDisposed) return
    after(project, getAfterVirtualFiles(events))
    forwardEvents(events)
  }

  open fun forwardEvents(events: MutableList<out VFileEvent>) {}

  abstract fun after(project: Project, virtualFilesAffected: Set<VirtualFile>)

  /** **************************** Common/Util methods ************************* */
  private fun transformEventToNewVirtualFile(e: VFileEvent): VirtualFile? =
    when (e) {
      is VFileCopyEvent -> e.findCreatedFile()
      is VFileMoveEvent -> if (e.newParent.isValid) e.newParent.findChild(e.file.name) else null
      else -> e.file
    }

  private fun getBeforeVirtualFiles(events: List<VFileEvent>) =
    getAffectedVirtualFiles(
      events,
      eventToVirtualFileTransformer = { it.file },
      classesOfEventsToFilter =
        listOf(
          VFileDeleteEvent::class.java,
          VFileContentChangeEvent::class.java,
          VFileMoveEvent::class.java,
          VFilePropertyChangeEvent::class.java,
        ),
    )

  private fun getAfterVirtualFiles(events: List<VFileEvent>) =
    getAffectedVirtualFiles(
      events,
      eventToVirtualFileTransformer = { transformEventToNewVirtualFile(it) },
      classesOfEventsToFilter =
        listOf(
          VFileCreateEvent::class.java,
          VFileContentChangeEvent::class.java,
          VFileMoveEvent::class.java,
          VFileCopyEvent::class.java,
          VFilePropertyChangeEvent::class.java,
        ),
    )

  private fun getAffectedVirtualFiles(
    events: List<VFileEvent>,
    eventToVirtualFileTransformer: (VFileEvent) -> VirtualFile?,
    classesOfEventsToFilter: Collection<Class<*>>,
    eventsFilter: (VFileEvent) -> Boolean = { (it as? VFilePropertyChangeEvent)?.isRename != false },
  ): Set<VirtualFile> =
    events
      .asSequence()
      .filter { event -> instanceOf(event, classesOfEventsToFilter) }
      .filter { eventsFilter.invoke(it) }
      .mapNotNull(eventToVirtualFileTransformer)
      .filter(VirtualFile::isValid)
      .filter(VirtualFile::isFile)
      .toSet()

  private fun instanceOf(obj: Any, classes: Collection<Class<*>>): Boolean {
    for (c in classes) {
      if (c.isInstance(obj)) return true
    }
    return false
  }
}
