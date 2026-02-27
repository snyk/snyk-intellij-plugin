package io.snyk.plugin

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.apache.commons.lang3.SystemProperties
import org.apache.commons.lang3.SystemUtils
import org.junit.After
import org.junit.Before
import org.junit.Test

class UtilsKtTest {

  @Before
  fun setUp() {
    unmockkAll()
    mockkStatic(SystemProperties::class)
    every { SystemProperties.getOsName() } returns "Windows"
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `toLanguageServerURL (windows)`() {
    unmockkAll()
    if (!SystemUtils.IS_OS_WINDOWS) return
    val path = "C:\\Users\\username\\file.txt"
    val virtualFile = mockk<VirtualFile>()
    every { virtualFile.path } returns path

    assertEquals("file:///C:/Users/username/file.txt", virtualFile.toLanguageServerURI())
  }

  @Test
  fun `toLanguageServerURL (posix)`() {
    unmockkAll()
    if (SystemUtils.IS_OS_WINDOWS) return
    val path = "/Users/username/file.txt"
    val virtualFile = mockk<VirtualFile>()
    every { virtualFile.path } returns path

    assertEquals("file:///Users/username/file.txt", virtualFile.toLanguageServerURI())
  }

  @Test
  fun `toLanguageServerURI removes trailing slashes`() {
    unmockkAll()
    val tempDir = java.nio.file.Files.createTempDirectory("snyk-test-dir")
    try {
      val path = tempDir.toAbsolutePath().toString()
      val virtualFile = mockk<VirtualFile>()
      every { virtualFile.path } returns path

      val uri = virtualFile.toLanguageServerURI()
      assertFalse("URI should not end with slash: $uri", uri.endsWith("/"))
      assertTrue("URI should start with file:", uri.startsWith("file:"))
    } finally {
      java.nio.file.Files.deleteIfExists(tempDir)
    }
  }

  @Test
  fun isAdditionalParametersValid() {
    assertFalse(isAdditionalParametersValid("-d"))
    assertTrue(isAdditionalParametersValid("asdf"))
  }

  @Test
  fun `conversion between path and uri - ensure we can convert a URI to a path and back (posix)`() {
    unmockkAll()
    if (SystemUtils.IS_OS_WINDOWS) return
    val expectedPaths =
      arrayOf(
        "/Users/username/file.txt",
        "/Users/Username/file.txt",
        "/Users/user name/file.txt",
        "/Users/user name/hyphenated - folder/file.txt",
      )
    val inputUris =
      arrayOf(
        "file:///Users/username/file.txt", // URI
        "file:///Users/Username/file.txt", // URI
        "file:///Users/user%20name/file.txt", // URI with space
        "file:///Users/user%20name/hyphenated%20-%20folder/file.txt", // URI with hyphen and space
      )

    var i = 0
    for (uri in inputUris) {
      val actualPath = uri.fromUriToPath().toString()
      assertEquals("Testing $uri to path conversion", expectedPaths[i], actualPath)
      assertEquals("Testing $actualPath to uri conversion", uri, actualPath.fromPathToUriString())
      i++
    }
  }

  @Test
  fun `conversion between path and uri - ensure we can convert a URI to a path and back (windows)`() {
    unmockkAll()
    if (!SystemUtils.IS_OS_WINDOWS) return
    val expectedPaths =
      arrayOf(
        "C:\\Users\\username\\file.txt", // Valid path
        "c:\\Users\\username\\file.txt", // Valid path
        "C:\\Users\\username with spaces\\file.txt", // Valid path
        "C:\\Users\\username with hyphenated - spaces\\file.txt", // Valid path
        "C:\\Users\\username with \$peci@l characters\\file.txt", // Valid path
        "\\\\myserver\\shared folder\\file name with spaces \$peci@l%.txt",
      )

    val inputUris =
      arrayOf(
        "file:///C:/Users/username/file.txt", // Valid URI
        "file:///c:/Users/username/file.txt", // Valid URI
        "file:///C:/Users/username%20with%20spaces/file.txt", // Valid URI
        "file:///C:/Users/username%20with%20hyphenated%20-%20spaces/file.txt", // Valid URI
        "file:///C:/Users/username%20with%20\$peci@l%20characters/file.txt", // Valid URI
        "file://myserver/shared%20folder/file%20name%20with%20spaces%20\$peci@l%25.txt", // UNC
      )

    var i = 0
    for (uri in inputUris) {
      val actualPath = uri.fromUriToPath().toString()
      assertEquals("Testing $uri to path conversion", expectedPaths[i], actualPath)
      assertEquals("Testing $actualPath to uri conversion", uri, actualPath.fromPathToUriString())
      i++
    }
  }

  @Test
  fun `isCliInstalled returns true in unit test mode`() {
    unmockkAll()
    val appMock = mockk<com.intellij.openapi.application.Application>(relaxed = true)
    mockkStatic(ApplicationManager::class)
    every { ApplicationManager.getApplication() } returns appMock
    every { appMock.isUnitTestMode } returns true

    // In unit test mode, isCliInstalled always returns true
    assertTrue(isCliInstalled())
  }

  @Test
  fun `isCliInstalled checks file exists and is executable`() {
    unmockkAll()
    val appMock = mockk<com.intellij.openapi.application.Application>(relaxed = true)
    mockkStatic(ApplicationManager::class)
    every { ApplicationManager.getApplication() } returns appMock
    every { appMock.isUnitTestMode } returns false

    val tempFile = File.createTempFile("snyk-cli-test", ".exe")
    try {
      mockkStatic(::getCliFile)
      every { getCliFile() } returns tempFile

      // On Windows, setExecutable(false) doesn't work reliably - canExecute() returns true
      // for most files based on extension. Only test the positive case on all platforms.
      if (!SystemUtils.IS_OS_WINDOWS) {
        // File exists but not executable (Unix only)
        tempFile.setExecutable(false)
        assertFalse(isCliInstalled())
      }

      // File exists and is executable
      tempFile.setExecutable(true)
      assertTrue(isCliInstalled())
    } finally {
      tempFile.delete()
    }
  }

  @Test
  fun `isCliInstalled returns false when file does not exist`() {
    unmockkAll()
    val appMock = mockk<com.intellij.openapi.application.Application>(relaxed = true)
    mockkStatic(ApplicationManager::class)
    every { ApplicationManager.getApplication() } returns appMock
    every { appMock.isUnitTestMode } returns false

    val nonExistentFile = File("/non/existent/path/snyk-cli")
    mockkStatic(::getCliFile)
    every { getCliFile() } returns nonExistentFile

    assertFalse(isCliInstalled())
  }

  @Test
  fun `publishAsync publishes event asynchronously`() {
    unmockkAll()

    val project = mockk<Project>(relaxed = true)
    val messageBus = mockk<MessageBus>(relaxed = true)
    val listener = mockk<TestListener>(relaxed = true)

    every { project.isDisposed } returns false
    every { project.messageBus } returns messageBus
    every { messageBus.isDisposed } returns false
    every { messageBus.syncPublisher(any<Topic<TestListener>>()) } returns listener

    val latch = CountDownLatch(1)
    every { listener.onEvent() } answers { latch.countDown() }

    val topic = Topic.create("test-topic", TestListener::class.java)
    publishAsync(project, topic) { onEvent() }

    // Wait for async execution
    assertTrue("Async publish should complete within timeout", latch.await(2, TimeUnit.SECONDS))
    verify { listener.onEvent() }
  }

  @Test
  fun `publishAsync does not publish when project is disposed`() {
    unmockkAll()

    val project = mockk<Project>(relaxed = true)
    val messageBus = mockk<MessageBus>(relaxed = true)
    mockk<TestListener>(relaxed = true)

    every { project.isDisposed } returns true
    every { project.messageBus } returns messageBus

    val topic = Topic.create("test-topic", TestListener::class.java)
    publishAsync(project, topic) { onEvent() }

    // Give some time for potential async execution
    Thread.sleep(100)

    // Should not have accessed messageBus.syncPublisher since project is disposed
    verify(exactly = 0) { messageBus.syncPublisher(any<Topic<TestListener>>()) }
  }

  @Test
  fun `publishAsync does not publish when messageBus is disposed`() {
    unmockkAll()

    val project = mockk<Project>(relaxed = true)
    val messageBus = mockk<MessageBus>(relaxed = true)

    every { project.isDisposed } returns false
    every { project.messageBus } returns messageBus
    every { messageBus.isDisposed } returns true

    val topic = Topic.create("test-topic", TestListener::class.java)
    publishAsync(project, topic) { onEvent() }

    // Give some time for potential async execution
    Thread.sleep(100)

    // Should not have called syncPublisher since messageBus is disposed
    verify(exactly = 0) { messageBus.syncPublisher(any<Topic<TestListener>>()) }
  }

  @Test
  fun `publishAsyncApp publishes event asynchronously on application message bus`() {
    unmockkAll()

    val appMock = mockk<com.intellij.openapi.application.Application>(relaxed = true)
    val messageBus = mockk<MessageBus>(relaxed = true)
    val listener = mockk<TestListener>(relaxed = true)

    mockkStatic(ApplicationManager::class)
    every { ApplicationManager.getApplication() } returns appMock
    every { appMock.isDisposed } returns false
    every { appMock.messageBus } returns messageBus
    every { messageBus.syncPublisher(any<Topic<TestListener>>()) } returns listener

    val latch = CountDownLatch(1)
    every { listener.onEvent() } answers { latch.countDown() }

    val topic = Topic.create("test-app-topic", TestListener::class.java)
    publishAsyncApp(topic) { onEvent() }

    // Wait for async execution
    assertTrue("Async app publish should complete within timeout", latch.await(2, TimeUnit.SECONDS))
    verify { listener.onEvent() }
  }

  @Test
  fun `publishAsyncApp does not publish when application is disposed`() {
    unmockkAll()

    val appMock = mockk<com.intellij.openapi.application.Application>(relaxed = true)
    val messageBus = mockk<MessageBus>(relaxed = true)

    mockkStatic(ApplicationManager::class)
    every { ApplicationManager.getApplication() } returns appMock
    every { appMock.isDisposed } returns true
    every { appMock.messageBus } returns messageBus

    val topic = Topic.create("test-app-topic", TestListener::class.java)
    publishAsyncApp(topic) { onEvent() }

    // Give some time for potential async execution
    Thread.sleep(100)

    // Should not have accessed messageBus.syncPublisher since application is disposed
    verify(exactly = 0) { messageBus.syncPublisher(any<Topic<TestListener>>()) }
  }

  @Test
  fun `navigateToSource should still open file when getDocument returns null`() {
    unmockkAll()
    mockkStatic("io.snyk.plugin.UtilsKt")
    mockkStatic(ApplicationManager::class)
    mockkStatic(PsiNavigationSupport::class)

    val appMock = mockk<com.intellij.openapi.application.Application>(relaxed = true)
    every { ApplicationManager.getApplication() } returns appMock
    every { appMock.isDisposed } returns false
    // Mock invokeLater to execute runnables immediately
    // The top-level invokeLater delegates to Application.invokeLater with ModalityState
    every { appMock.invokeLater(any<Runnable>()) } answers { firstArg<Runnable>().run() }
    every {
      appMock.invokeLater(any<Runnable>(), any<com.intellij.openapi.application.ModalityState>())
    } answers { firstArg<Runnable>().run() }

    val project = mockk<Project>(relaxed = true)
    every { project.isDisposed } returns false

    val virtualFile = mockk<VirtualFile>(relaxed = true)
    every { virtualFile.isValid } returns true
    // Simulate a file type that IntelliJ doesn't recognize (e.g., .gitleaksignore)
    every { virtualFile.getDocument() } returns null

    val latch = CountDownLatch(1)
    val navigatable = mockk<Navigatable>(relaxed = true)
    every { navigatable.canNavigateToSource() } returns true
    every { navigatable.canNavigate() } returns true
    every { navigatable.navigate(any()) } answers { latch.countDown() }

    val psiNavSupport = mockk<PsiNavigationSupport>(relaxed = true)
    every { PsiNavigationSupport.getInstance() } returns psiNavSupport
    every { psiNavSupport.createNavigatable(project, virtualFile, 5) } returns navigatable

    navigateToSource(project, virtualFile, 5, 10)

    assertTrue("Navigation should complete within timeout", latch.await(2, TimeUnit.SECONDS))
    verify { navigatable.navigate(false) }
  }

  @Test
  fun `navigateToSource should still open file when offset is out of bounds`() {
    unmockkAll()
    mockkStatic("io.snyk.plugin.UtilsKt")
    mockkStatic(ApplicationManager::class)
    mockkStatic(PsiNavigationSupport::class)

    val appMock = mockk<com.intellij.openapi.application.Application>(relaxed = true)
    every { ApplicationManager.getApplication() } returns appMock
    every { appMock.isDisposed } returns false
    every { appMock.invokeLater(any<Runnable>()) } answers { firstArg<Runnable>().run() }
    every {
      appMock.invokeLater(any<Runnable>(), any<com.intellij.openapi.application.ModalityState>())
    } answers { firstArg<Runnable>().run() }

    val project = mockk<Project>(relaxed = true)
    every { project.isDisposed } returns false

    val document = mockk<com.intellij.openapi.editor.Document>(relaxed = true)
    every { document.textLength } returns 50

    val virtualFile = mockk<VirtualFile>(relaxed = true)
    every { virtualFile.isValid } returns true
    every { virtualFile.getDocument() } returns document

    // Offset 100 is beyond document length of 50
    val latch = CountDownLatch(1)
    val navigatable = mockk<Navigatable>(relaxed = true)
    every { navigatable.canNavigateToSource() } returns true
    every { navigatable.canNavigate() } returns true
    every { navigatable.navigate(any()) } answers { latch.countDown() }

    val psiNavSupport = mockk<PsiNavigationSupport>(relaxed = true)
    every { PsiNavigationSupport.getInstance() } returns psiNavSupport
    every { psiNavSupport.createNavigatable(project, virtualFile, 100) } returns navigatable

    navigateToSource(project, virtualFile, 100, 110)

    assertTrue("Navigation should complete within timeout", latch.await(2, TimeUnit.SECONDS))
    verify { navigatable.navigate(false) }
  }

  interface TestListener {
    fun onEvent()
  }
}
