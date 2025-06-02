package snyk.common

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.isPreCommitCheckEnabled
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import snyk.common.lsp.LanguageServerWrapper

class PreCommitHookHandler : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
        val project = panel.project
        return object : CheckinHandler() {
            override fun beforeCheckin(): ReturnResult {
                if (!isPreCommitCheckEnabled()) return ReturnResult.COMMIT

                val snykCachedResults = getSnykCachedResults(project) ?: return ReturnResult.COMMIT
                val noCodeIssues = snykCachedResults.currentSnykCodeResultsLS.isEmpty()
                val noOSSIssues = snykCachedResults.currentOSSResultsLS.isEmpty()
                val noIaCIssues = snykCachedResults.currentIacResultsLS.isEmpty()
                val noContainerIssues = (snykCachedResults.currentContainerResult?.issuesCount ?: 0) == 0
                LanguageServerWrapper.getInstance(project).sendScanCommand()
                val doCommit = noCodeIssues && noOSSIssues && noIaCIssues && noContainerIssues
                if (!doCommit) {
                    SnykBalloonNotificationHelper.showWarn("Stopped commit, because of you have issues.", project)
                    return ReturnResult.CANCEL
                }
                return ReturnResult.COMMIT
            }
        }
    }



}
