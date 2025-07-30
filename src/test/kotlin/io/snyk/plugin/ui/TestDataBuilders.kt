package io.snyk.plugin.ui

import io.mockk.mockk
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import snyk.common.SnykError
import snyk.common.lsp.FolderConfig
import snyk.common.lsp.ScanIssue
import snyk.common.lsp.SnykScanParams

/**
 * Test data builders for UI tests
 * Uses existing test resources when possible to avoid creating new test data
 */
object TestDataBuilders {
    /**
     * Create a mock Snyk scan parameters
     */
    fun createMockScanParams(
        folderPath: String = "/test/project",
        product: String = "oss"
    ): SnykScanParams {
        return SnykScanParams(
            status = "inProgress",
            product = product,
            folderPath = folderPath,
            issues = emptyList()
        )
    }

    /**
     * Create a mock folder configuration
     */
    fun createMockFolderConfig(
        folderPath: String = "/test/project",
        baseBranch: String = "main"
    ): FolderConfig {
        return FolderConfig(
            folderPath = folderPath,
            baseBranch = baseBranch,
            localBranches = listOf("main", "develop", "feature/test"),
            referenceFolderPath = null
        )
    }

    /**
     * Create a mock scan issue
     * Uses mockk to avoid complex IssueData construction
     */
    fun createMockScanIssue(
        id: String = "test-issue-1",
        title: String = "Test Security Issue",
        severity: String = "high",
        filePath: String = "/test/file.java",
        range: Range = createMockRange()
    ): ScanIssue {
        return ScanIssue(
            id = id,
            title = title,
            severity = severity,
            filePath = filePath,
            range = range,
            additionalData = mockk(relaxed = true),
            isIgnored = false,
            isNew = false,
            filterableIssueType = ScanIssue.CODE_SECURITY,
            ignoreDetails = null
        )
    }

    /**
     * Create mock OSS scan issue
     */
    fun createMockOssScanIssue(
        id: String = "oss-issue-1",
        title: String = "Vulnerable dependency",
        packageName: String = "test-package",
        version: String = "1.0.0"
    ): ScanIssue {
        return createMockScanIssue(
            id = id,
            title = title,
            severity = "high"
        ).apply {
            filterableIssueType = ScanIssue.OPEN_SOURCE
        }
    }

    /**
     * Create mock IaC scan issue
     */
    fun createMockIacScanIssue(
        id: String = "iac-issue-1",
        title: String = "Insecure configuration",
        filePath: String = "/test/terraform.tf"
    ): ScanIssue {
        return createMockScanIssue(
            id = id,
            title = title,
            filePath = filePath
        ).apply {
            filterableIssueType = ScanIssue.INFRASTRUCTURE_AS_CODE
        }
    }

    /**
     * Create a mock range
     */
    fun createMockRange(
        startLine: Int = 10,
        startChar: Int = 0,
        endLine: Int = 10,
        endChar: Int = 50
    ): Range {
        return Range(
            Position(startLine, startChar),
            Position(endLine, endChar)
        )
    }

    /**
     * Create a mock Snyk error
     */
    fun createMockSnykError(
        message: String = "Test error occurred",
        path: String = "/test/project",
        code: Int = 1
    ): SnykError {
        return SnykError(
            message = message,
            path = path,
            code = code
        )
    }
}