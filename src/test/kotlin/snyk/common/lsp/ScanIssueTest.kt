package snyk.common.lsp

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.snyk.plugin.getDocument
import io.snyk.plugin.toVirtualFile
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScanIssueTest {

  @Before
  fun setUp() {
    mockkStatic("io.snyk.plugin.UtilsKt")
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `textRange should handle out of bounds lines gracefully`() {
    // Arrange
    val project = mockk<Project>()
    val virtualFile = mockk<VirtualFile>()
    val document = mockk<Document>()

    val filePath = "/path/to/file.kt"

    every { filePath.toVirtualFile() } returns virtualFile
    every { virtualFile.getDocument() } returns document

    // Document has 10 lines (0-9) and length 100
    every { document.lineCount } returns 10
    every { document.textLength } returns 100
    // Valid line 5 starts at offset 50, ends at 59
    every { document.getLineStartOffset(5) } returns 50
    every { document.getLineEndOffset(5) } returns 59

    // Create an issue with a range that goes out of bounds
    // Start: valid (line 5, char 0) -> offset 50
    // End: invalid (line 20, char 0) -> should be clamped to 100
    val range = Range(Position(5, 0), Position(20, 0))

    val issue =
      ScanIssue(
        id = "test-issue",
        title = "Test Issue",
        severity = "high",
        filePath = filePath,
        range = range,
        additionalData = mockk(relaxed = true),
        isIgnored = false,
        isNew = true,
        filterableIssueType = ScanIssue.CODE_SECURITY,
        ignoreDetails = null,
      )

    // Act
    val textRange = issue.textRange

    // Assert
    assertEquals(50, textRange?.startOffset)
    assertEquals(100, textRange?.endOffset)
  }

  @Test
  fun `textRange should handle negative lines gracefully`() {
    // Arrange
    val project = mockk<Project>()
    val virtualFile = mockk<VirtualFile>()
    val document = mockk<Document>()

    val filePath = "/path/to/file.kt"

    every { filePath.toVirtualFile() } returns virtualFile
    every { virtualFile.getDocument() } returns document

    every { document.lineCount } returns 10
    every { document.textLength } returns 100

    // Start: invalid negative line (-1) -> should be clamped to 0
    // End: valid line 5 -> offset 50
    val range = Range(Position(-1, 0), Position(5, 0))
    every { document.getLineStartOffset(5) } returns 50
    every { document.getLineEndOffset(5) } returns 59

    val issue =
      ScanIssue(
        id = "test-issue",
        title = "Test Issue",
        severity = "high",
        filePath = filePath,
        range = range,
        additionalData = mockk(relaxed = true),
        isIgnored = false,
        isNew = true,
        filterableIssueType = ScanIssue.CODE_SECURITY,
        ignoreDetails = null,
      )

    // Act
    val textRange = issue.textRange

    // Assert
    assertEquals(0, textRange?.startOffset)
    assertEquals(50, textRange?.endOffset)
  }

  private fun createIssueWithType(
    filterableType: String,
    title: String = "Hardcoded Secret: password in config",
    packageName: String = "",
    version: String = "",
    riskScore: Int = 0,
    priorityScore: Int = 0,
    severity: String = "high",
    details: String? = "some details",
    cwe: List<String>? = listOf("CWE-798"),
    cvssScore: String? = null,
    CVSSv3: String? = null,
    ruleId: String = "rule-1",
  ): ScanIssue {
    val virtualFile = mockk<VirtualFile>()
    val document = mockk<Document>()
    val filePath = "/path/to/file.kt"

    every { filePath.toVirtualFile() } returns virtualFile
    every { virtualFile.getDocument() } returns document
    every { document.lineCount } returns 10
    every { document.textLength } returns 100
    every { document.getLineStartOffset(any()) } returns 0
    every { document.getLineEndOffset(any()) } returns 10

    return ScanIssue(
      id = "test-issue",
      title = title,
      severity = severity,
      filePath = filePath,
      range = Range(Position(0, 0), Position(0, 10)),
      additionalData =
        IssueData(
          message = "Test message",
          leadURL = "",
          rule = "",
          repoDatasetSize = 0,
          exampleCommitFixes = listOf(),
          cwe = cwe,
          text = "",
          markers = null,
          cols = null,
          rows = null,
          isSecurityType = true,
          priorityScore = priorityScore,
          hasAIFix = false,
          dataFlow = listOf(),
          license = null,
          identifiers = null,
          description = "",
          language = "",
          packageManager = "",
          packageName = packageName,
          name = "",
          version = version,
          exploit = null,
          CVSSv3 = CVSSv3,
          cvssScore = cvssScore,
          fixedIn = null,
          from = listOf(),
          upgradePath = listOf(),
          isPatchable = false,
          isUpgradable = false,
          projectName = "",
          displayTargetFile = null,
          matchingIssues = listOf(),
          lesson = null,
          details = details,
          ruleId = ruleId,
          publicId = "",
          documentation = "",
          lineNumber = "",
          issue = "",
          impact = "",
          resolve = "",
          path = emptyList(),
          references = emptyList(),
          customUIContent = null,
          key = "key-1",
          riskScore = riskScore,
        ),
      isIgnored = false,
      isNew = false,
      filterableIssueType = filterableType,
      ignoreDetails = null,
    )
  }

  @Test
  fun `title() splits on colon for SECRETS type`() {
    val issue =
      createIssueWithType(ScanIssue.SECRETS, title = "Hardcoded Secret: password in config")
    assertEquals("Hardcoded Secret", issue.title())
  }

  @Test
  fun `title() splits on colon for CODE_SECURITY type`() {
    val issue = createIssueWithType(ScanIssue.CODE_SECURITY, title = "SQL Injection: user input")
    assertEquals("SQL Injection", issue.title())
  }

  @Test
  fun `title() returns full title for OPEN_SOURCE type`() {
    val issue = createIssueWithType(ScanIssue.OPEN_SOURCE, title = "Prototype Pollution")
    assertEquals("Prototype Pollution", issue.title())
  }

  @Test
  fun `issueNaming() returns Secrets Issue for SECRETS type`() {
    val issue = createIssueWithType(ScanIssue.SECRETS)
    assertEquals("Secrets Issue", issue.issueNaming())
  }

  data class PriorityTestCase(
    val name: String,
    val filterableType: String,
    val riskScore: Int,
    val priorityScore: Int,
    val severity: String,
    val expectedMinPriority: Int,
  )

  @Test
  fun `priority() uses riskScore when positive, priorityScore otherwise`() {
    val testCases =
      listOf(
        PriorityTestCase(
          name = "SECRETS with riskScore",
          filterableType = ScanIssue.SECRETS,
          riskScore = 500,
          priorityScore = 0,
          severity = "high",
          expectedMinPriority = 3_000_000,
        ),
        PriorityTestCase(
          name = "SECRETS with priorityScore only",
          filterableType = ScanIssue.SECRETS,
          riskScore = 0,
          priorityScore = 300,
          severity = "medium",
          expectedMinPriority = 2_000_000,
        ),
        PriorityTestCase(
          name = "CODE_SECURITY with priorityScore",
          filterableType = ScanIssue.CODE_SECURITY,
          riskScore = 0,
          priorityScore = 400,
          severity = "critical",
          expectedMinPriority = 4_000_000,
        ),
      )

    for (tc in testCases) {
      val issue =
        createIssueWithType(
          tc.filterableType,
          riskScore = tc.riskScore,
          priorityScore = tc.priorityScore,
          severity = tc.severity,
        )
      val priority = issue.priority()
      assertTrue(
        "${tc.name}: expected >= ${tc.expectedMinPriority}, got $priority",
        priority >= tc.expectedMinPriority,
      )
    }
  }

  @Test
  fun `longTitle() includes range bracket for all types`() {
    val testCases =
      listOf(
        ScanIssue.SECRETS to "",
        ScanIssue.CODE_SECURITY to "",
        ScanIssue.OPEN_SOURCE to "lodash",
      )

    for ((type, pkg) in testCases) {
      val issue =
        createIssueWithType(
          type,
          packageName = pkg,
          version = if (pkg.isNotEmpty()) "4.17.21" else "",
        )
      val longTitle = issue.longTitle()
      assertTrue(
        "longTitle for $type should contain range bracket: $longTitle",
        longTitle.contains("[1,0]"),
      )
      if (pkg.isNotEmpty()) {
        assertTrue(
          "longTitle for $type should contain package info: $longTitle",
          longTitle.contains("$pkg@"),
        )
      }
    }
  }

  @Test
  fun `cwes() returns combined CWE list from both sources`() {
    val issue = createIssueWithType(ScanIssue.SECRETS, cwe = listOf("CWE-798", "CWE-259"))
    val cwes = issue.cwes()
    assertTrue("Should contain CWE-798", cwes.contains("CWE-798"))
    assertTrue("Should contain CWE-259", cwes.contains("CWE-259"))
  }

  @Test
  fun `cves() returns empty for SECRETS`() {
    val issue = createIssueWithType(ScanIssue.SECRETS)
    assertTrue(issue.cves().isEmpty())
  }

  @Test
  fun `cvssScore() returns null when not set`() {
    val issue = createIssueWithType(ScanIssue.SECRETS)
    assertNull(issue.cvssScore())
  }

  @Test
  fun `id() returns null for SECRETS type`() {
    val issue = createIssueWithType(ScanIssue.SECRETS)
    assertNull(issue.id())
  }

  @Test
  fun `textRange should clamp offset if character exceeds length`() {
    // Arrange
    val project = mockk<Project>()
    val virtualFile = mockk<VirtualFile>()
    val document = mockk<Document>()

    val filePath = "/path/to/file.kt"

    every { filePath.toVirtualFile() } returns virtualFile
    every { virtualFile.getDocument() } returns document

    every { document.lineCount } returns 10
    every { document.textLength } returns 100
    every { document.getLineStartOffset(9) } returns 90
    every { document.getLineEndOffset(9) } returns 100

    // Start: valid line 9, but character 20 (90 + 20 = 110) > 100 -> should be clamped to 100
    val range = Range(Position(9, 20), Position(9, 25))

    val issue =
      ScanIssue(
        id = "test-issue",
        title = "Test Issue",
        severity = "high",
        filePath = filePath,
        range = range,
        additionalData = mockk(relaxed = true),
        isIgnored = false,
        isNew = true,
        filterableIssueType = ScanIssue.CODE_SECURITY,
        ignoreDetails = null,
      )

    // Act
    val textRange = issue.textRange

    // Assert
    assertEquals(100, textRange?.startOffset)
    assertEquals(100, textRange?.endOffset)
  }
}
