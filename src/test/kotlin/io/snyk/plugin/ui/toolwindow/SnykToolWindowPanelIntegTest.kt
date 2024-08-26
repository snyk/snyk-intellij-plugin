@file:Suppress("UNCHECKED_CAST")

package io.snyk.plugin.ui.toolwindow

import com.intellij.ide.util.gotoByName.GotoFileCellRenderer
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ui.tree.TreeUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.snyk.plugin.Severity
import io.snyk.plugin.cli.ConsoleCommandRunner
import io.snyk.plugin.events.SnykResultsFilteringListener
import io.snyk.plugin.events.SnykScanListener
import io.snyk.plugin.getContainerService
import io.snyk.plugin.getIacService
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.getOssService
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.setupDummyCliFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.actions.SnykTreeMediumSeverityFilterAction
import io.snyk.plugin.ui.toolwindow.nodes.leaf.VulnerabilityTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.ErrorTreeNode
import io.snyk.plugin.ui.toolwindow.nodes.secondlevel.FileTreeNode
import io.snyk.plugin.ui.toolwindow.panels.SnykErrorPanel
import io.snyk.plugin.ui.toolwindow.panels.VulnerabilityDescriptionPanel
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.services.LanguageServer
import org.junit.Test
import snyk.common.SnykError
import snyk.common.UIComponentFinder
import snyk.common.lsp.LanguageServerWrapper
import snyk.common.lsp.commands.COMMAND_EXECUTE_CLI
import snyk.container.ContainerIssue
import snyk.container.ContainerIssuesForImage
import snyk.container.ContainerResult
import snyk.container.ContainerService
import snyk.container.Docker
import snyk.container.KubernetesWorkloadImage
import snyk.container.ui.BaseImageRemediationDetailPanel
import snyk.container.ui.ContainerImageTreeNode
import snyk.container.ui.ContainerIssueDetailPanel
import snyk.container.ui.ContainerIssueTreeNode
import snyk.iac.IacError
import snyk.iac.IacIssue
import snyk.iac.IacIssuesForFile
import snyk.iac.IacResult
import snyk.iac.IacScanService
import snyk.iac.IacSuggestionDescriptionPanel
import snyk.iac.IgnoreButtonActionListener
import snyk.iac.ui.toolwindow.IacFileTreeNode
import snyk.iac.ui.toolwindow.IacIssueTreeNode
import snyk.oss.Vulnerability
import snyk.trust.confirmScanningAndSetWorkspaceTrustedStateIfNeeded
import java.util.concurrent.CompletableFuture
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.tree.TreeNode

class SnykToolWindowPanelIntegTest : HeavyPlatformTestCase() {

    private val iacGoofJson = getResourceAsString("iac-test-results/infrastructure-as-code-goof.json")
    private val ossGoofJson = getResourceAsString("oss-test-results/oss-result-package.json")
    private val containerResultWithRemediationJson =
        getResourceAsString("container-test-results/nginx-with-remediation.json")

    private fun getResourceAsString(resourceName: String): String = javaClass.classLoader
        .getResource(resourceName)!!.readText(Charsets.UTF_8)

    private lateinit var toolWindowPanel: SnykToolWindowPanel
    private val scanPublisher
        get() = getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)!!

    private val fakeApiToken = "fake_token"
    private val lsMock = mockk<LanguageServer>(relaxed = true)

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        mockkStatic("snyk.trust.TrustedProjectsKt")
        val settings = pluginSettings()
        settings.token = fakeApiToken // needed to avoid forced Auth panel showing
        settings.pluginFirstRun = false
        settings.cliReleaseChannel = "preview"
        // ToolWindow need to be reinitialised for every test as Project is recreated for Heavy tests
        // also we MUST do it *before* any actual test code due to initialisation of SnykScanListener in init{}
        toolWindowPanel = project.service()
        setupDummyCliFile()
        every { confirmScanningAndSetWorkspaceTrustedStateIfNeeded(any()) } returns true
        mockkStatic(GotoFileCellRenderer::class)
        every { GotoFileCellRenderer.getRelativePath(any(), any()) } returns "abc/"
        val languageServerWrapper = LanguageServerWrapper.getInstance()
        languageServerWrapper.isInitialized = true
        languageServerWrapper.languageServer = lsMock
        languageServerWrapper.process = mockk(relaxed = true)
        languageServerWrapper.languageClient = mockk(relaxed = true)
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        try {
            super.tearDown()
        } catch (ignore: Exception) {
            // nothing to do here
        }
    }

    private fun setUpIacTest() {
        val settings = pluginSettings()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = true
        settings.containerScanEnabled = false
    }

    private fun setUpContainerTest(containerResultStub: ContainerResult?) {
        val settings = pluginSettings()
        settings.ossScanEnable = false
        settings.snykCodeSecurityIssuesScanEnable = false
        settings.snykCodeQualityIssuesScanEnable = false
        settings.iacScanEnabled = false
        settings.containerScanEnabled = true

        if (containerResultStub != null) {
            mockkStatic("io.snyk.plugin.UtilsKt")
            every { getContainerService(project)?.scan() } returns containerResultStub
        }
    }

    private fun prepareTreeWithFakeOssResults() {
        val param =
            ExecuteCommandParams(COMMAND_EXECUTE_CLI, listOf(project.basePath, "test", "--json", "--all-projects"))

        every { lsMock.workspaceService.executeCommand(param) } returns
            CompletableFuture.completedFuture(mapOf(Pair("stdOut", ossGoofJson)))

        val ossResult = getOssService(project)?.scan()!!
        scanPublisher.scanningOssFinished(ossResult)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
    }

    private fun prepareTreeWithFakeIacResults() {
        setUpIacTest()

        val lsMock = mockk<LanguageServer>()
        LanguageServerWrapper.getInstance().languageServer = lsMock
        val param = ExecuteCommandParams(COMMAND_EXECUTE_CLI, listOf(project.basePath, "iac", "test", "--json"))

        every { lsMock.workspaceService.executeCommand(param) } returns
            CompletableFuture.completedFuture(mapOf(Pair("stdOut", iacGoofJson)))

        val ignoreParam = ExecuteCommandParams(
            COMMAND_EXECUTE_CLI,
            listOf(
                project.basePath,
                "ignore",
                "--id=SNYK-CC-TF-53",
                "--path=* > [DocId:0] > Resources > LaunchConfig > Properties > BlockDeviceMappings"
            )
        )

        every { lsMock.workspaceService.executeCommand(ignoreParam) } returns
            CompletableFuture.completedFuture(mapOf(Pair("stdOut", "")))

        val iacResult = project.service<IacScanService>().scan()
        scanPublisher.scanningIacFinished(iacResult)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
    }

    private val fakeContainerIssue1 = ContainerIssue(
        id = "fakeId1",
        title = "fakeTitle1",
        description = "fakeDescription1",
        severity = "low",
        from = emptyList(),
        packageManager = "fakePackageManager1"
    )

    private val fakeContainerIssue2 = ContainerIssue(
        id = "fakeId2",
        title = "fakeTitle2",
        description = "fakeDescription2",
        severity = "low",
        from = emptyList(),
        packageManager = "fakePackageManager2"
    )

    private val fakeContainerResult = ContainerResult(
        listOf(
            ContainerIssuesForImage(
                vulnerabilities = listOf(fakeContainerIssue1),
                projectName = "fake project name",
                docker = Docker(),
                uniqueCount = 1,
                error = null,
                imageName = "fake-image-name1"
            ),
            ContainerIssuesForImage(
                vulnerabilities = listOf(fakeContainerIssue2),
                projectName = "fake project name",
                docker = Docker(),
                uniqueCount = 1,
                error = null,
                imageName = "fake-image-name2"
            )
        )
    )

    private val fakeContainerResultWithError = ContainerResult(
        listOf(
            ContainerIssuesForImage(
                vulnerabilities = listOf(fakeContainerIssue1),
                projectName = "fake project name",
                docker = Docker(),
                uniqueCount = 1,
                error = null,
                imageName = "fake-image-name1"
            )
        ),
        listOf(
            SnykError(
                message = "fake error message",
                path = "fake-path"
            )
        )
    )

    fun `test when no OSS supported file found should display special text (not error) in node and description`() {
        mockkObject(SnykBalloonNotificationHelper)

        val snykError = SnykError(SnykToolWindowPanel.NO_OSS_FILES, project.basePath.toString())
        val snykErrorControl = SnykError("control", project.basePath.toString())

        scanPublisher.scanningOssError(snykErrorControl)
        scanPublisher.scanningOssError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val rootOssTreeNode = toolWindowPanel.getRootOssIssuesTreeNode()
        // flow and internal state check
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(any(), project)
        }
        assertTrue(getSnykCachedResults(project)?.currentOssError == null)
        assertTrue(getSnykCachedResults(project)?.currentOssResults == null)
        val cliErrorMessage = rootOssTreeNode.originalCliErrorMessage
        assertTrue(cliErrorMessage != null && cliErrorMessage.startsWith(SnykToolWindowPanel.NO_OSS_FILES))
        // node check
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + SnykToolWindowPanel.NO_SUPPORTED_PACKAGE_MANAGER_FOUND,
            rootOssTreeNode.userObject
        )
        // description check
        TreeUtil.selectNode(toolWindowPanel.getTree(), rootOssTreeNode)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
        val jEditorPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class
        )
        assertNotNull(jEditorPane)
        jEditorPane!!
        assertTrue(jEditorPane.text.contains(SnykToolWindowPanel.NO_OSS_FILES))
    }

    @Test
    fun `test when no IAC supported file found should display special text (not error) in node and description`() {
        mockkObject(SnykBalloonNotificationHelper)

        val snykError =
            SnykError(SnykToolWindowPanel.NO_IAC_FILES, project.basePath.toString(), IacError.NO_IAC_FILES_CODE)

        val snykErrorControl = SnykError("control", project.basePath.toString())

        scanPublisher.scanningIacError(snykErrorControl)
        scanPublisher.scanningIacError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        // flow and internal state check
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykErrorControl.message, project)
        }

        assertTrue(getSnykCachedResults(project)?.currentIacError == null)
        assertTrue(getSnykCachedResults(project)?.currentIacResult == null)
        // node check
        assertEquals(
            SnykToolWindowPanel.IAC_ROOT_TEXT + SnykToolWindowPanel.NO_SUPPORTED_IAC_FILES_FOUND,
            toolWindowPanel.getRootIacIssuesTreeNode().userObject
        )
        // description check
        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootIacIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
        val jEditorPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class
        )
        assertNotNull(jEditorPane)
        jEditorPane!!
        assertTrue(jEditorPane.text.contains(SnykToolWindowPanel.NO_IAC_FILES))
    }

    @Test
    fun `test should ignore IaC failures in IaC scan results (no issues found)`() {
        mockkObject(SnykBalloonNotificationHelper)
        val jsonError = SnykError("Failed to parse JSON file", project.basePath.toString(), 1021)
        val inputError = SnykError("Failed to parse input", project.basePath.toString(), 2105)
        val iacResult = IacResult(emptyList(), listOf(jsonError, inputError))
        scanPublisher.scanningIacFinished(iacResult)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(
            SnykToolWindowPanel.IAC_ROOT_TEXT + SnykToolWindowPanel.NO_ISSUES_FOUND_TEXT,
            toolWindowPanel.getRootIacIssuesTreeNode().userObject
        )
    }

    fun `test should ignore IaC failures in IaC scan results (issues found)`() {
        mockkObject(SnykBalloonNotificationHelper)
        val iacIssue = IacIssue(
            id = "SNYK-CC-TF-74",
            title = "Credentials are configured via provider attributes",
            lineNumber = 1,
            severity = "",
            publicId = "",
            documentation = "",
            issue = "",
            impact = ""
        )
        val iacIssuesForFile =
            IacIssuesForFile(listOf(iacIssue), "k8s-deployment.yaml", "src/k8s-deployment.yaml", "npm", null, project)
        val jsonError = SnykError("Failed to parse JSON file", project.basePath.toString(), 1021)
        val iacResult = IacResult(listOf(iacIssuesForFile), listOf(jsonError))
        scanPublisher.scanningIacFinished(iacResult)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(
            SnykToolWindowPanel.IAC_ROOT_TEXT + " - 1 unique issue",
            toolWindowPanel.getRootIacIssuesTreeNode().userObject
        )
    }

    fun `test should display NO_CONTAINER_IMAGES_FOUND after scan when no Container images found`() {
        mockkObject(SnykBalloonNotificationHelper)

        val snykError = ContainerService.NO_IMAGES_TO_SCAN_ERROR
        val snykErrorControl = SnykError("control", project.basePath.toString())

        scanPublisher.scanningContainerError(snykErrorControl)
        scanPublisher.scanningContainerError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(any(), project)
        }

        assertTrue(getSnykCachedResults(project)?.currentContainerError == null)
        assertEquals(
            SnykToolWindowPanel.CONTAINER_ROOT_TEXT + SnykToolWindowPanel.NO_CONTAINER_IMAGES_FOUND,
            toolWindowPanel.getRootContainerIssuesTreeNode().userObject
        )
    }

    fun `test should display CONTAINER_NO_IMAGES_FOUND_TEXT after scan when no Container images found and Container node selected`() {
        val snykError = ContainerService.NO_IMAGES_TO_SCAN_ERROR

        scanPublisher.scanningContainerError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val noImagesFoundPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class,
            SnykToolWindowPanel.CONTAINER_NO_IMAGES_FOUND_TEXT
        )
        assertNotNull(noImagesFoundPane)
    }

    fun `test should display CONTAINER_NO_ISSUES_FOUND_TEXT after scan when no Container issues found and Container node selected`() {
        scanPublisher.scanningContainerFinished(ContainerResult(emptyList()))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val noIssuesFoundPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class,
            SnykToolWindowPanel.CONTAINER_NO_ISSUES_FOUND_TEXT
        )
        assertNotNull(noIssuesFoundPane)
    }

    fun `test should display CONTAINER_SCAN_START_TEXT before any scan performed and Container node selected`() {
        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val startContainerScanPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class,
            SnykToolWindowPanel.CONTAINER_SCAN_START_TEXT
        )
        assertNotNull(startContainerScanPane)
    }

    fun `test should display CONTAINER_SCAN_RUNNING_TEXT before any scan performed and Container node selected`() {
        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
        scanPublisher.scanningStarted()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val containerScanRunningPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class,
            SnykToolWindowPanel.CONTAINER_SCAN_RUNNING_TEXT
        )
        assertNotNull(containerScanRunningPane)
    }

    fun `test OSS scan should redirect to Auth panel if token is invalid`() {
        mockkObject(SnykBalloonNotificationHelper)
        val snykErrorControl = SnykError("control", project.basePath.toString())
        val snykError = SnykError("Authentication failed. Please check the API token on ", project.basePath.toString())

        scanPublisher.scanningOssError(snykErrorControl)
        scanPublisher.scanningOssError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykErrorControl.message, project)
        }
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykError.message, project)
        }
        assertTrue(getSnykCachedResults(project)?.currentOssError == null)
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT,
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )
        val authPanel = UIComponentFinder.getComponentByName(toolWindowPanel, JPanel::class, "authPanel")
        assertNotNull(authPanel)
    }

    fun `test Container scan should redirect to Auth panel if token is invalid`() {
        mockkObject(SnykBalloonNotificationHelper)
        val snykErrorControl = SnykError("control", project.basePath.toString())
        val snykError = SnykError("Authentication failed. Please check the API token on ", project.basePath.toString())

        scanPublisher.scanningContainerError(snykErrorControl)
        scanPublisher.scanningContainerError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykErrorControl.message, project)
        }
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykError.message, project)
        }
        assertTrue(getSnykCachedResults(project)?.currentContainerError == null)
        assertEquals(
            SnykToolWindowPanel.CONTAINER_ROOT_TEXT,
            toolWindowPanel.getRootContainerIssuesTreeNode().userObject
        )
        val authPanel = UIComponentFinder.getComponentByName(toolWindowPanel, JPanel::class, "authPanel")
        assertNotNull(authPanel)
    }

    fun `test Container node should show no child when disabled`() {
        mockkObject(SnykBalloonNotificationHelper)
        val rootContainerNode = toolWindowPanel.getRootContainerIssuesTreeNode()

        // assert child shown when Container results exist
        getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)?.scanningContainerFinished(fakeContainerResult)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertEquals(fakeContainerResult, getSnykCachedResults(project)?.currentContainerResult)
        assertTrue(rootContainerNode.childCount > 0)

        // assert no child shown when Container node disabled
        pluginSettings().containerScanEnabled = false
        getSyncPublisher(project, SnykResultsFilteringListener.SNYK_FILTERING_TOPIC)?.filtersChanged()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(rootContainerNode.childCount == 0)
    }

    fun `test should display '(error)' in OSS root tree node when result is empty and error occurs`() {
        val snykError = SnykError("an error", project.basePath.toString())
        scanPublisher.scanningOssError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(getSnykCachedResults(project)?.currentOssError == snykError)
        assertTrue(getSnykCachedResults(project)?.currentOssResults == null)
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + " (error)",
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )
    }

    fun `test should display 'scanning' in OSS root tree node when it is scanning`() {
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { isOssRunning(project) } returns true
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        toolWindowPanel.updateTreeRootNodesPresentation(null, 0, 0, 0)
        assertTrue(getSnykCachedResults(project)?.currentOssResults == null)
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + " (scanning...)",
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )
    }

    fun testSeverityFilterForIacResult() {
        // pre-test setup
        prepareTreeWithFakeIacResults()

        // actual test run
        val rootIacIssuesTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        fun isMediumSeverityShown(): Boolean = rootIacIssuesTreeNode.children().asSequence()
            .flatMap { (it as TreeNode).children().asSequence() }
            .any {
                it is IacIssueTreeNode &&
                    it.userObject is IacIssue &&
                    (it.userObject as IacIssue).getSeverity() == Severity.MEDIUM
            }

        assertTrue("Medium severity IaC results should be shown by default", isMediumSeverityShown())

        val mediumSeverityFilterAction =
            ActionManager.getInstance().getAction("io.snyk.plugin.ui.actions.SnykTreeMediumSeverityFilterAction")
                as SnykTreeMediumSeverityFilterAction
        mediumSeverityFilterAction.setSelected(TestActionEvent.createTestEvent(), false)

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertFalse("Medium severity IaC results should NOT be shown after filtering", isMediumSeverityShown())
    }

    fun testIacErrorShown() {
        // pre-test setup
        setUpIacTest()

        // mock IaC results
        val iacError = SnykError("fake error", "fake path")
        val iacResultWithError = IacResult(null, listOf(iacError))

        mockkStatic("io.snyk.plugin.UtilsKt")
        every { getIacService(project)?.scan() } returns iacResultWithError

        // actual test run
        project.service<SnykTaskQueueService>().scan(false)

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertEquals(iacError, getSnykCachedResults(project)?.currentIacError)

        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootIacIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val descriptionComponents = toolWindowPanel.getDescriptionPanel().components.toList()
        val errorPanel = descriptionComponents.find { it is SnykErrorPanel } as SnykErrorPanel?

        assertNotNull(errorPanel)

        val errorMessageTextArea =
            UIComponentFinder.getComponentByName(errorPanel!!, JTextArea::class, "errorMessageTextArea")
        val pathTextArea = UIComponentFinder.getComponentByName(errorPanel, JTextArea::class, "pathTextArea")

        assertTrue(errorMessageTextArea?.text == iacError.message)
        assertTrue(pathTextArea?.text == iacError.path)
    }

    fun test_WhenIacIssueIgnored_ThenItMarkedIgnored_AndButtonRemainsDisabled() {
        // pre-test setup
        prepareTreeWithFakeIacResults()

        val tree = toolWindowPanel.getTree()

        // select first IaC issue and ignore it
        val rootIacIssuesTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        val firstIaCFileNode = rootIacIssuesTreeNode.firstChild as? IacFileTreeNode
        val firstIacIssueNode = firstIaCFileNode?.firstChild as? IacIssueTreeNode
            ?: throw IllegalStateException("IacIssueNode should not be null")
        TreeUtil.selectNode(tree, firstIacIssueNode)
        waitWhileTreeBusy()

        fun iacDescriptionPanel() =
            UIComponentFinder.getComponentByName(
                toolWindowPanel.getDescriptionPanel(),
                IacSuggestionDescriptionPanel::class,
                "IacSuggestionDescriptionPanel"
            ) ?: throw IllegalStateException("IacSuggestionDescriptionPanel should not be null")

        val ignoreButton = UIComponentFinder.getComponentByName(iacDescriptionPanel(), JButton::class, "ignoreButton")
            ?: throw IllegalStateException("IgnoreButton should not be null")

        assertFalse(
            "Issue should NOT be ignored by default",
            (firstIacIssueNode.userObject as IacIssue).ignored
        )
        assertTrue(
            "Ignore Button should be enabled by default",
            ignoreButton.isEnabled && ignoreButton.text != IgnoreButtonActionListener.IGNORED_ISSUE_BUTTON_TEXT
        )

        ignoreButton.doClick()

        // check final state
        assertTrue(
            "Issue should be marked as ignored after ignoring",
            (firstIacIssueNode.userObject as IacIssue).ignored
        )
        assertTrue(
            "Ignore Button should be disabled for ignored issue",
            !ignoreButton.isEnabled && ignoreButton.text == IgnoreButtonActionListener.IGNORED_ISSUE_BUTTON_TEXT
        )
        PlatformTestUtil.waitWhileBusy(tree)
        TreeUtil.selectNode(tree, firstIacIssueNode.nextNode)
        PlatformTestUtil.waitWhileBusy(tree)
        TreeUtil.selectNode(tree, firstIacIssueNode)
        PlatformTestUtil.waitWhileBusy(tree)
        assertTrue(
            "Ignore Button should remain disabled for ignored issue",
            !ignoreButton.isEnabled && ignoreButton.text == IgnoreButtonActionListener.IGNORED_ISSUE_BUTTON_TEXT
        )
    }

    fun `test all root nodes are shown`() {
        setUpIacTest()
        setUpContainerTest(null)

        val tree = toolWindowPanel.getTree()
        PlatformTestUtil.waitWhileBusy(tree)

        val rootNode = toolWindowPanel.getRootNode()
        setOf(
            SnykToolWindowPanel.OSS_ROOT_TEXT,
            SnykToolWindowPanel.CODE_SECURITY_ROOT_TEXT,
            SnykToolWindowPanel.CODE_QUALITY_ROOT_TEXT,
            SnykToolWindowPanel.IAC_ROOT_TEXT,
            SnykToolWindowPanel.CONTAINER_ROOT_TEXT
        ).forEach {
            assertTrue(
                "Root node for [$it] not found",
                TreeUtil.findNodeWithObject(rootNode, it) != null
            )
        }
    }

    fun `test container error shown`() {
        // mock Container results
        val containerError = SnykError("fake error", "fake path")
        val containerResultWithError = ContainerResult(null, listOf(containerError))

        setUpContainerTest(containerResultWithError)

        // actual test run
        project.service<SnykTaskQueueService>().scan(false)

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertEquals(containerError, getSnykCachedResults(project)?.currentContainerError)

        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val descriptionComponents = toolWindowPanel.getDescriptionPanel().components.toList()
        val errorPanel = descriptionComponents.find { it is SnykErrorPanel } as? SnykErrorPanel

        assertNotNull(errorPanel)

        val errorMessageTextArea =
            UIComponentFinder.getComponentByName(errorPanel!!, JTextArea::class, "errorMessageTextArea")
        val pathTextArea = UIComponentFinder.getComponentByName(errorPanel, JTextArea::class, "pathTextArea")

        assertTrue(errorMessageTextArea?.text == containerError.message)
        assertTrue(pathTextArea?.text == containerError.path)
    }

    fun `test container image nodes with description shown`() {
        // pre-test setup
        setUpContainerTest(fakeContainerResult)

        // actual test run
        project.service<SnykTaskQueueService>().scan(false)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        // Assertions
        assertEquals(fakeContainerResult, getSnykCachedResults(project)?.currentContainerResult)

        val rootContainerNode = toolWindowPanel.getRootContainerIssuesTreeNode()
        assertEquals("2 images with issues should be found", 2, rootContainerNode.childCount)
        assertEquals(
            "`fake-image-name1` should be found",
            "fake-image-name1",
            ((rootContainerNode.firstChild as ContainerImageTreeNode).userObject as ContainerIssuesForImage).imageName
        )
        assertEquals(
            "`fake-image-name2` should be found",
            "fake-image-name2",
            ((rootContainerNode.lastChild as ContainerImageTreeNode).userObject as ContainerIssuesForImage).imageName
        )

        TreeUtil.selectNode(toolWindowPanel.getTree(), rootContainerNode.firstChild)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
        val baseImageRemediationDetailPanel = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            BaseImageRemediationDetailPanel::class
        )
        assertNotNull(baseImageRemediationDetailPanel)
    }

    fun `test container image node and Failed-to-scan image shown`() {
        // pre-test setup
        setUpContainerTest(fakeContainerResultWithError)
        mockkObject(SnykBalloonNotificationHelper)

        // actual test run
        project.service<SnykTaskQueueService>().scan(false)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        // Assertions
        assertEquals(fakeContainerResultWithError, getSnykCachedResults(project)?.currentContainerResult)

        val rootContainerNode = toolWindowPanel.getRootContainerIssuesTreeNode()
        assertEquals("1 image with issues and 1 error should be found", 2, rootContainerNode.childCount)
        assertEquals(
            "`fake-image-name1` should be found",
            "fake-image-name1",
            ((rootContainerNode.firstChild as ContainerImageTreeNode).userObject as ContainerIssuesForImage).imageName
        )
        assertTrue(
            "`fake-path` should be found",
            ((rootContainerNode.lastChild as ErrorTreeNode).userObject as SnykError).path.startsWith("fake-path")
        )
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(
                match { it.contains("fake-path") },
                project,
                any()
            )
        }
    }

    fun `test container issue nodes with description shown`() {
        // pre-test setup
        setUpContainerTest(fakeContainerResult)

        // actual test run
        project.service<SnykTaskQueueService>().scan(false)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        // Assertions
        val rootContainerNode = toolWindowPanel.getRootContainerIssuesTreeNode()
        val issueNode =
            (rootContainerNode.firstChild as? ContainerImageTreeNode)?.firstChild as? ContainerIssueTreeNode
        assertNotNull("Container issue node should be found in the Tree", issueNode)
        issueNode!!
        assertEquals(
            "`fake container issue` should be inside Container issue node",
            fakeContainerIssue1,
            issueNode.userObject as ContainerIssue
        )

        TreeUtil.selectNode(toolWindowPanel.getTree(), issueNode)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
        val containerIssueDetailPanel = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            ContainerIssueDetailPanel::class
        )
        assertNotNull("ContainerIssueDetailPanel should be shown on issue node selection", containerIssueDetailPanel)
    }

    fun `test container image nodes with remediation description shown`() {
        prepareContainerTreeNodesAndCaches(containerResultWithRemediationJson)

        val rootContainerNode = toolWindowPanel.getRootContainerIssuesTreeNode()
        TreeUtil.selectNode(toolWindowPanel.getTree(), rootContainerNode.firstChild)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        // Assertions
        val currentImageValueLabel = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JLabel::class,
            BaseImageRemediationDetailPanel.CURRENT_IMAGE
        )
        assertNotNull(currentImageValueLabel)
        assertEquals("current image incorrect", "nginx:1.16.0", currentImageValueLabel?.text)

        val minorUpgradeValueLabel = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JLabel::class,
            BaseImageRemediationDetailPanel.MINOR_UPGRADES
        )
        assertNotNull(minorUpgradeValueLabel)
        assertEquals("minor upgrades incorrect", "nginx:1.20.2", minorUpgradeValueLabel?.text)

        val majorUpgradeValueLabel = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JLabel::class,
            BaseImageRemediationDetailPanel.MAJOR_UPGRADES
        )
        assertNotNull(minorUpgradeValueLabel)
        assertEquals("major upgrades incorrect", "nginx:1.21.4", majorUpgradeValueLabel?.text)

        val alternativeUpgradeValueLabel = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JLabel::class,
            BaseImageRemediationDetailPanel.ALTERNATIVE_UPGRADES
        )
        assertNotNull(alternativeUpgradeValueLabel)
        assertEquals("alternative upgrades incorrect", "nginx:1-perl", alternativeUpgradeValueLabel?.text)
    }

    private fun prepareContainerTreeNodesAndCaches(containerResultJson: String): ContainerResult {
        // mock Container results
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { getKubernetesImageCache(project)?.getKubernetesWorkloadImages() } returns setOf(
            KubernetesWorkloadImage("ignored_image_name", MockVirtualFile("fake_virtual_file"))
        )
        every { getKubernetesImageCache(project)?.getKubernetesWorkloadImageNamesFromCache() } returns
            setOf("ignored_image_name")
        val containerService = ContainerService(project)
        val mockkRunner = mockk<ConsoleCommandRunner>()
        every { mockkRunner.execute(any(), any(), any(), project) } returns containerResultJson
        containerService.setConsoleCommandRunner(mockkRunner)

        val containerResult = containerService.scan()
        setUpContainerTest(containerResult)

        // actual test run
        project.service<SnykTaskQueueService>().scan(false)
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        return containerResult
    }

    fun `test container image node has correct amount of leaf(issue) nodes`() {
        val containerResult = prepareContainerTreeNodesAndCaches(containerResultWithRemediationJson)

        val rootContainerNode = toolWindowPanel.getRootContainerIssuesTreeNode()

        val expectedIssuesCount = containerResult.issuesCount
        val actualIssueNodesCount = rootContainerNode.children().asSequence().sumOf {
            (it as TreeNode).childCount
        }
        assertEquals(expectedIssuesCount, actualIssueNodesCount)
    }

    private fun waitWhileTreeBusy() {
        val tree = toolWindowPanel.getTree()
        PlatformTestUtil.waitWhileBusy(tree)
    }

    fun `test IaC node selected and Description shown on external request`() {
        // pre-test setup
        prepareTreeWithFakeIacResults()

        val rootIacIssuesTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        val firstIaCFileNode = rootIacIssuesTreeNode.firstChild as? IacFileTreeNode
        val firstIacIssueNode = firstIaCFileNode?.firstChild as? IacIssueTreeNode
            ?: throw IllegalStateException("IacIssueNode should not be null")
        val iacIssue = firstIacIssueNode.userObject as IacIssue

        // actual test run
        toolWindowPanel.selectNodeAndDisplayDescription(iacIssue)
        waitWhileTreeBusy()

        // Assertions
        val selectedNodeUserObject = TreeUtil.findObjectInPath(toolWindowPanel.getTree().selectionPath, Any::class.java)
        assertEquals(iacIssue, selectedNodeUserObject)

        val iacDescriptionPanel =
            UIComponentFinder.getComponentByName(
                toolWindowPanel.getDescriptionPanel(),
                IacSuggestionDescriptionPanel::class,
                "IacSuggestionDescriptionPanel"
            )
        assertNotNull("IacSuggestionDescriptionPanel should not be null", iacDescriptionPanel)
    }

    fun `test OSS node selected and Description shown on external request`() {
        prepareTreeWithFakeOssResults()

        val rootOssIssuesTreeNode = toolWindowPanel.getRootOssIssuesTreeNode()
        val firstOssFileNode = rootOssIssuesTreeNode.firstChild as FileTreeNode
        val firstOssIssueNode = firstOssFileNode.firstChild as VulnerabilityTreeNode
        val groupedVulns = firstOssIssueNode.userObject as Collection<Vulnerability>
        val vulnerability = groupedVulns.first()

        // actual test run
        toolWindowPanel.selectNodeAndDisplayDescription(vulnerability)
        waitWhileTreeBusy()

        // Assertions
        val selectedNodeUserObject = TreeUtil.findObjectInPath(toolWindowPanel.getTree().selectionPath, Any::class.java)
        assertEquals(groupedVulns, selectedNodeUserObject)

        val vulnerabilityDescriptionPanel =
            UIComponentFinder.getComponentByName(
                toolWindowPanel.getDescriptionPanel(),
                VulnerabilityDescriptionPanel::class
            )
        assertNotNull("VulnerabilityDescriptionPanel should not be null", vulnerabilityDescriptionPanel)
    }

    fun `test Container node selected and Description shown on external request`() {
        // prepare Tree with fake Container results
        setUpContainerTest(null)
        getSyncPublisher(project, SnykScanListener.SNYK_SCAN_TOPIC)?.scanningContainerFinished(fakeContainerResult)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        val rootContainerTreeNode = toolWindowPanel.getRootContainerIssuesTreeNode()
        val firstImageNode = rootContainerTreeNode.firstChild as ContainerImageTreeNode
        val containerImage = firstImageNode.userObject as ContainerIssuesForImage

        // actual test run
        toolWindowPanel.selectNodeAndDisplayDescription(containerImage)
        waitWhileTreeBusy()

        // Assertions
        val selectedNodeUserObject = TreeUtil.findObjectInPath(toolWindowPanel.getTree().selectionPath, Any::class.java)
        assertEquals(containerImage, selectedNodeUserObject)

        val containerImageDescriptionPanel =
            UIComponentFinder.getComponentByName(
                toolWindowPanel.getDescriptionPanel(),
                BaseImageRemediationDetailPanel::class
            )
        assertNotNull("Image's Description Panel should not be null", containerImageDescriptionPanel)
    }
}
