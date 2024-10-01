/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Copyright (c) 2024 Snyk Ltd
 *
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Snyk Ltd - adjustments for use in Snyk IntelliJ Plugin
 *******************************************************************************/
package snyk.common.lsp.progress


import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Disposer
import io.snyk.plugin.ui.toolwindow.SnykPluginDisposable
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.WorkDoneProgressBegin
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkDoneProgressKind
import org.eclipse.lsp4j.WorkDoneProgressNotification
import org.eclipse.lsp4j.WorkDoneProgressReport
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Function


class ProgressManager : Disposable {
    private val progresses: MutableMap<String, Progress> = ConcurrentHashMap<String, Progress>()
    private var disposed = false
        get() {
            return SnykPluginDisposable.getInstance().isDisposed() || field
        }

    fun isDisposed() = disposed

    init {
        Disposer.register(SnykPluginDisposable.getInstance(), this)
    }

    fun createProgress(params: WorkDoneProgressCreateParams): CompletableFuture<Void> {
        if (!disposed) {
            val token = getToken(params.token)
            getProgress(token)
        }
        return CompletableFuture.completedFuture(null)
    }

    private fun createProgressIndicator(progress: Progress) {
        val token: String = progress.token
        if (isDone(progress)) {
            progresses.remove(token)
            return
        }
        val title = "Snyk: " + progress.title
        val cancellable: Boolean = progress.cancellable
        ProgressManager.getInstance()
            .run(newProgressBackgroundTask(title, cancellable, progress, token))
    }

    private fun newProgressBackgroundTask(
        title: String,
        cancellable: Boolean,
        progress: Progress,
        token: String
    ): Task.Backgroundable {
        val project = ProjectUtil.getActiveProject()
        return object : Task.Backgroundable(project, title, cancellable) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    while (!isDone(progress)) {
                        if (indicator.isCanceled) {
                            cancelProgress(token)
                            throw ProcessCanceledException()
                        }

                        var progressNotification: WorkDoneProgressNotification?
                        try {
                            progressNotification = progress.nextProgressNotification
                        } catch (e: InterruptedException) {
                            progresses.remove(token)
                            Thread.currentThread().interrupt()
                            throw ProcessCanceledException(e)
                        }
                        if (progressNotification != null) {
                            val kind = progressNotification.kind ?: return
                            when (kind) {
                                WorkDoneProgressKind.begin ->  // 'begin' has been notified
                                    begin(progressNotification as WorkDoneProgressBegin, indicator)

                                WorkDoneProgressKind.report ->  // 'report' has been notified
                                    report(progressNotification as WorkDoneProgressReport, indicator)

                                WorkDoneProgressKind.end -> Unit
                            }
                        }
                    }
                } finally {
                    indicator.cancel()
                    progresses.remove(token)
                }
            }
        }
    }

    private fun cancelProgress(token: String) {
        try {
            progresses[token]?.cancel()
        } finally {
            progresses.remove(token)
        }
    }

    private fun isDone(progress: Progress): Boolean {
        return progress.done || progress.cancelled || disposed
    }

    private fun begin(
        begin: WorkDoneProgressBegin,
        progressIndicator: ProgressIndicator
    ) {
        val percentage = begin.percentage
        progressIndicator.isIndeterminate = percentage == null
        updateProgressIndicator(begin.message, percentage, progressIndicator)
    }

    private fun report(
        report: WorkDoneProgressReport,
        progressIndicator: ProgressIndicator
    ) {
        updateProgressIndicator(report.message, report.percentage, progressIndicator)
    }

    @Synchronized
    private fun getProgress(token: String): Progress {
        var progress: Progress? = progresses[token]
        if (progress != null) {
            return progress
        }
        progress = Progress(token)
        progresses[token] = progress
        return progress
    }

    private fun updateProgressIndicator(
        message: String?,
        percentage: Int?,
        progressIndicator: ProgressIndicator
    ) {
        if (!message.isNullOrBlank()) {
            progressIndicator.text = message
        }
        if (percentage != null) {
            progressIndicator.isIndeterminate = false
            progressIndicator.fraction = percentage.toDouble() / 100
        }
    }

    fun cancelProgresses() {
        if (disposed) return

        progresses.values.forEach(Progress::cancel)
    }

    fun notifyProgress(params: ProgressParams) {
        if (params.value == null || params.token == null || disposed) {
            return
        }
        val value = params.value
        if (value.isRight) {
            // we don't need partial results progress support
            return
        }

        if (!value.isLeft) {
            return
        }

        val progressNotification = value.left
        val kind = progressNotification.kind ?: return
        val token = getToken(params.token)
        var progress: Progress? = progresses[token]
        if (progress == null) {
            // The server is not spec-compliant and reports progress using server-initiated progress but didn't
            // call window/workDoneProgress/create beforehand. In that case, we check the 'kind' field of the
            // progress data. If the 'kind' field is 'begin', we set up a progress reporter anyway.
            if (kind != WorkDoneProgressKind.begin) {
                return
            }
            progress = getProgress(token)
        }

        // Add the progress notification
        progress.add(progressNotification)
        when (progressNotification.kind!!) {
            WorkDoneProgressKind.begin -> {
                // 'begin' progress
                val begin = progressNotification as WorkDoneProgressBegin
                progress.title = begin.title
                progress.cancellable = begin.cancellable != null && begin.cancellable
                // The IJ task is created on 'begin' and not on 'create' to initialize
                // the Task with the 'begin' title.
                createProgressIndicator(progress)
            }

            WorkDoneProgressKind.end -> progress.done = true
            WorkDoneProgressKind.report -> Unit
        }
    }

    override fun dispose() {
        this.disposed = true
        progresses.values.forEach(Progress::cancel)
        progresses.clear()
    }

    companion object {
        private fun getToken(token: Either<String, Int>): String {
            return token.map(
                Function.identity()
            ) { obj: Int -> obj.toString() }
        }
    }
}
