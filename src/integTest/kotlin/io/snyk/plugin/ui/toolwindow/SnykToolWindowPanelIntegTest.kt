package io.snyk.plugin.ui.toolwindow

import UIComponentFinder
import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
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
import io.snyk.plugin.getCliFile
import io.snyk.plugin.getContainerService
import io.snyk.plugin.getIacService
import io.snyk.plugin.getKubernetesImageCache
import io.snyk.plugin.getSnykCachedResults
import io.snyk.plugin.getSyncPublisher
import io.snyk.plugin.isCliInstalled
import io.snyk.plugin.isOssRunning
import io.snyk.plugin.pluginSettings
import io.snyk.plugin.removeDummyCliFile
import io.snyk.plugin.resetSettings
import io.snyk.plugin.services.SnykTaskQueueService
import io.snyk.plugin.setupDummyCliFile
import io.snyk.plugin.ui.SnykBalloonNotificationHelper
import io.snyk.plugin.ui.actions.SnykTreeMediumSeverityFilterAction
import org.junit.Test
import snyk.common.SnykError
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
import snyk.iac.IacIssue
import snyk.iac.IacResult
import snyk.iac.IacSuggestionDescriptionPanel
import snyk.iac.IgnoreButtonActionListener
import snyk.iac.ui.toolwindow.IacFileTreeNode
import snyk.iac.ui.toolwindow.IacIssueTreeNode
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.tree.TreeNode

@Suppress("FunctionName")
class SnykToolWindowPanelIntegTest : HeavyPlatformTestCase() {

    private val iacGoofJson = getResourceAsString("iac-test-results/infrastructure-as-code-goof.json")
    private val containerResultWithRemediationJson =
        getResourceAsString("container-test-results/nginx-with-remediation.json")

    private fun getResourceAsString(resourceName: String): String = javaClass.classLoader
        .getResource(resourceName)!!.readText(Charsets.UTF_8)

    private lateinit var toolWindowPanel: SnykToolWindowPanel

    override fun setUp() {
        super.setUp()
        unmockkAll()
        resetSettings(project)
        // ToolWindow need to be reinitialised for every test as Project is recreated for Heavy tests
        // also we MUST do it *before* any actual test code due to initialisation of SnykScanListener in init{}
        toolWindowPanel = project.service()
        setupDummyCliFile()
    }

    override fun tearDown() {
        unmockkAll()
        resetSettings(project)
        removeDummyCliFile()
        super.tearDown()
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
            every { isCliInstalled() } returns true
            every { getContainerService(project)?.scan() } returns containerResultStub
        }
    }

    private fun prepareTreeWithFakeIacResults() {
        setUpIacTest()

        val mockRunner = mockk<ConsoleCommandRunner>()
        every {
            mockRunner.execute(
                listOf(getCliFile().absolutePath, "iac", "test", "--json"),
                project.basePath!!,
                project = project
            )
        } returns (iacGoofJson)

        getIacService(project)?.setConsoleCommandRunner(mockRunner)

        project.service<SnykTaskQueueService>().scan()
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
                listOf(fakeContainerIssue1),
                "fake project name",
                Docker(),
                null,
                "fake-image-name1"
            ),
            ContainerIssuesForImage(
                listOf(fakeContainerIssue2),
                "fake project name",
                Docker(),
                null,
                "fake-image-name2"
            )
        ),
        null
    )

    @Test
    fun `test should not display error when no OSS supported file found`() {
        mockkObject(SnykBalloonNotificationHelper)

        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false

        val snykError = SnykError("Could not detect supported target files in", project.basePath.toString())
        val snykErrorControl = SnykError("control", project.basePath.toString())

        toolWindowPanel.snykScanListener.scanningOssError(snykErrorControl)
        toolWindowPanel.snykScanListener.scanningOssError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(any(), project)
        }
        assertTrue(toolWindowPanel.currentOssError == null)
        assertTrue(getSnykCachedResults(project)?.currentOssResults == null)
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + SnykToolWindowPanel.NO_SUPPORTED_PACKAGE_MANAGER_FOUND,
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )
    }

    @Test
    fun `test should not display error when no IAC supported file found`() {
        mockkObject(SnykBalloonNotificationHelper)

        val snykError = SnykError("Could not find any valid IaC files", project.basePath.toString())
        val snykErrorControl = SnykError("control", project.basePath.toString())

        toolWindowPanel.snykScanListener.scanningIacError(snykErrorControl)
        toolWindowPanel.snykScanListener.scanningIacError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(any(), project)
        }

        assertTrue(toolWindowPanel.currentIacError == null)
        assertEquals(
            SnykToolWindowPanel.IAC_ROOT_TEXT + SnykToolWindowPanel.NO_SUPPORTED_IAC_FILES_FOUND,
            toolWindowPanel.getRootIacIssuesTreeNode().userObject
        )
    }

    @Test
    fun `test should display NO_CONTAINER_IMAGES_FOUND after scan when no Container images found`() {
        mockkObject(SnykBalloonNotificationHelper)

        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false

        val snykError = ContainerService.NO_IMAGES_TO_SCAN_ERROR
        val snykErrorControl = SnykError("control", project.basePath.toString())

        toolWindowPanel.snykScanListener.scanningContainerError(snykErrorControl)
        toolWindowPanel.snykScanListener.scanningContainerError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(any(), project)
        }

        assertTrue(toolWindowPanel.currentContainerError == null)
        assertEquals(
            SnykToolWindowPanel.CONTAINER_ROOT_TEXT + SnykToolWindowPanel.NO_CONTAINER_IMAGES_FOUND,
            toolWindowPanel.getRootContainerIssuesTreeNode().userObject
        )
    }

    @Test
    fun `test should display CONTAINER_NO_IMAGES_FOUND_TEXT after scan when no Container images found and Container node selected`() {
        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false
        val snykError = ContainerService.NO_IMAGES_TO_SCAN_ERROR

        toolWindowPanel.snykScanListener.scanningContainerError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val noImagesFoundPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class,
            SnykToolWindowPanel.CONTAINER_NO_IMAGES_FOUND_TEXT)
        assertNotNull(noImagesFoundPane)
    }

    @Test
    fun `test should display CONTAINER_NO_ISSUES_FOUND_TEXT after scan when no Container issues found and Container node selected`() {
        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false

        toolWindowPanel.snykScanListener.scanningContainerFinished(ContainerResult(emptyList(), null))
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val noIssuesFoundPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class,
            SnykToolWindowPanel.CONTAINER_NO_ISSUES_FOUND_TEXT)
        assertNotNull(noIssuesFoundPane)
    }

    @Test
    fun `test should display CONTAINER_SCAN_START_TEXT before any scan performed and Container node selected`() {
        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false

        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val startContainerScanPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class,
            SnykToolWindowPanel.CONTAINER_SCAN_START_TEXT)
        assertNotNull(startContainerScanPane)
    }

    @Test
    fun `test should display CONTAINER_SCAN_RUNNING_TEXT before any scan performed and Container node selected`() {
        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false

        TreeUtil.selectNode(toolWindowPanel.getTree(), toolWindowPanel.getRootContainerIssuesTreeNode())
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
        toolWindowPanel.snykScanListener.scanningStarted()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        val containerScanRunningPane = UIComponentFinder.getComponentByName(
            toolWindowPanel.getDescriptionPanel(),
            JEditorPane::class,
            SnykToolWindowPanel.CONTAINER_SCAN_RUNNING_TEXT)
        assertNotNull(containerScanRunningPane)
    }

    @Test
    fun `test OSS scan should redirect to Auth panel if token is invalid`() {
        mockkObject(SnykBalloonNotificationHelper)
        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false
        val snykErrorControl = SnykError("control", project.basePath.toString())
        val snykError = SnykError("Authentication failed. Please check the API token on ", project.basePath.toString())

        toolWindowPanel.snykScanListener.scanningOssError(snykErrorControl)
        toolWindowPanel.snykScanListener.scanningOssError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykErrorControl.message, project)
        }
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykError.message, project)
        }
        assertTrue(toolWindowPanel.currentOssError == null)
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT,
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )
        val authPanel = UIComponentFinder.getComponentByName(toolWindowPanel, JPanel::class, "authPanel")
        assertNotNull(authPanel)
    }

    @Test
    fun `test Container scan should redirect to Auth panel if token is invalid`() {
        mockkObject(SnykBalloonNotificationHelper)
        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false
        val snykErrorControl = SnykError("control", project.basePath.toString())
        val snykError = SnykError("Authentication failed. Please check the API token on ", project.basePath.toString())

        toolWindowPanel.snykScanListener.scanningContainerError(snykErrorControl)
        toolWindowPanel.snykScanListener.scanningContainerError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykErrorControl.message, project)
        }
        verify(exactly = 1, timeout = 2000) {
            SnykBalloonNotificationHelper.showError(snykError.message, project)
        }
        assertTrue(toolWindowPanel.currentContainerError == null)
        assertEquals(
            SnykToolWindowPanel.CONTAINER_ROOT_TEXT,
            toolWindowPanel.getRootContainerIssuesTreeNode().userObject
        )
        val authPanel = UIComponentFinder.getComponentByName(toolWindowPanel, JPanel::class, "authPanel")
        assertNotNull(authPanel)
    }

    @Test
    fun `test Container node should show no child when disabled`() {
        mockkObject(SnykBalloonNotificationHelper)
        pluginSettings().token = "fake_token"
        pluginSettings().pluginFirstRun = false
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

    @Test
    fun `test should display '(error)' in OSS root tree node when result is empty and error occurs`() {
        val snykError = SnykError("an error", project.basePath.toString())
        toolWindowPanel.snykScanListener.scanningOssError(snykError)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

        assertTrue(toolWindowPanel.currentOssError == snykError)
        assertTrue(getSnykCachedResults(project)?.currentOssResults == null)
        assertEquals(
            SnykToolWindowPanel.OSS_ROOT_TEXT + " (error)",
            toolWindowPanel.getRootOssIssuesTreeNode().userObject
        )
    }

    @Test
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

    @Test
    fun testSeverityFilterForIacResult() {
        // pre-test setup
        prepareTreeWithFakeIacResults()

        // actual test run

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        val rootIacIssuesTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        fun isMediumSeverityShown(): Boolean = rootIacIssuesTreeNode.children().asSequence()
            .flatMap { (it as TreeNode).children().asSequence() }
            .any {
                it is IacIssueTreeNode &&
                    it.userObject is IacIssue &&
                    (it.userObject as IacIssue).severity == Severity.MEDIUM
            }

        assertTrue("Medium severity IaC results should be shown by default", isMediumSeverityShown())

        val mediumSeverityFilterAction =
            ActionManager.getInstance().getAction("io.snyk.plugin.ui.actions.SnykTreeMediumSeverityFilterAction")
                as SnykTreeMediumSeverityFilterAction
        mediumSeverityFilterAction.setSelected(TestActionEvent(), false)

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertFalse("Medium severity IaC results should NOT be shown after filtering", isMediumSeverityShown())
    }

    @Test
    fun testIacErrorShown() {
        // pre-test setup
        setUpIacTest()

        // mock IaC results
        val iacError = SnykError("fake error", "fake path")
        val iacResultWithError = IacResult(null, iacError)

        mockkStatic("io.snyk.plugin.UtilsKt")
        every { isCliInstalled() } returns true
        every { getIacService(project)?.scan() } returns iacResultWithError

        // actual test run
        project.service<SnykTaskQueueService>().scan()

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertEquals(iacError, toolWindowPanel.currentIacError)

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
        PlatformTestUtil.waitWhileBusy(tree)

        // select first IaC issue and ignore it
        val rootIacIssuesTreeNode = toolWindowPanel.getRootIacIssuesTreeNode()
        val firstIaCFileNode = rootIacIssuesTreeNode.firstChild as? IacFileTreeNode
        val firstIacIssueNode = firstIaCFileNode?.firstChild as? IacIssueTreeNode
            ?: throw IllegalStateException("IacIssueNode should not be null")
        TreeUtil.selectNode(tree, firstIacIssueNode)

        // hack to avoid "File accessed outside allowed roots" check in tests
        // needed due to com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.assertAccessInTests
        val prev_isInStressTest = ApplicationInfoImpl.isInStressTest()
        ApplicationInfoImpl.setInStressTest(true)
        try {
            PlatformTestUtil.waitWhileBusy(tree)
        } finally {
            ApplicationInfoImpl.setInStressTest(prev_isInStressTest)
        }

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

    @Test
    fun `test all root nodes are shown`() {
        setUpIacTest()
        setUpContainerTest(null)

        val tree = toolWindowPanel.getTree()
        PlatformTestUtil.waitWhileBusy(tree)

        val rootNode = toolWindowPanel.getRootNode()
        setOf(
            SnykToolWindowPanel.OSS_ROOT_TEXT,
            SnykToolWindowPanel.SNYKCODE_SECURITY_ISSUES_ROOT_TEXT,
            SnykToolWindowPanel.SNYKCODE_QUALITY_ISSUES_ROOT_TEXT,
            SnykToolWindowPanel.IAC_ROOT_TEXT,
            SnykToolWindowPanel.CONTAINER_ROOT_TEXT
        ).forEach {
            assertTrue(
                "Root node for [$it] not found",
                TreeUtil.findNodeWithObject(rootNode, it) != null
            )
        }
    }

    @Test
    fun `test container error shown`() {
        // mock Container results
        val containerError = SnykError("fake error", "fake path")
        val containerResultWithError = ContainerResult(null, containerError)

        setUpContainerTest(containerResultWithError)

        // actual test run
        project.service<SnykTaskQueueService>().scan()

        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())

        assertEquals(containerError, toolWindowPanel.currentContainerError)

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

    @Test
    fun `test container image nodes with description shown`() {
        // pre-test setup
        setUpContainerTest(fakeContainerResult)

        // actual test run
        project.service<SnykTaskQueueService>().scan()
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

    @Test
    fun `test container issue nodes with description shown`() {
        // pre-test setup
        setUpContainerTest(fakeContainerResult)

        // actual test run
        project.service<SnykTaskQueueService>().scan()
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

    @Test
    fun `test container image nodes with remediation description shown`() {
        // mock Container results
        mockkStatic("io.snyk.plugin.UtilsKt")
        every { getKubernetesImageCache(project)?.getKubernetesWorkloadImages() } returns setOf(
            KubernetesWorkloadImage("ignored_image_name", MockVirtualFile("fake_virtual_file"))
        )
        every { getKubernetesImageCache(project)?.getKubernetesWorkloadImageNamesFromCache() } returns
            setOf("ignored_image_name")
        val containerService = ContainerService(project)
        val mockkRunner = mockk<ConsoleCommandRunner>()
        every { mockkRunner.execute(any(), any(), any(), project) } returns containerResultWithRemediationJson
        containerService.setConsoleCommandRunner(mockkRunner)

        setUpContainerTest(containerService.scan())

        // actual test run
        project.service<SnykTaskQueueService>().scan()
        PlatformTestUtil.waitWhileBusy(toolWindowPanel.getTree())
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
}
