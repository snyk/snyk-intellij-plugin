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
