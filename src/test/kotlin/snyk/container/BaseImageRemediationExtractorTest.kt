package snyk.container

import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.notNullValue
import org.junit.Assert.assertThat
import org.junit.Test

class BaseImageRemediationExtractorTest {

    @Test
    fun extractImageInfo() {
        val input = """
Base Image    Vulnerabilities  Severity
nginx:1.16.0  188              23 critical, 44 high, 38 medium, 83 low
        """
        runTest(input, "nginx:1.16.0", 23, 44, 38, 83)
    }

    @Test
    fun extractImageInfo2() {
        val input = """Base Image     Vulnerabilities  Severity
postgres:13.1  114              5 critical, 11 high, 12 medium, 86 low
"""
        runTest(input, "postgres:13.1", 5, 11, 12, 86)
    }

    private fun runTest(
        input: String,
        expectedImage: String,
        expectedCritical: Int,
        expectedHigh: Int,
        expectedMedium: Int,
        expectedLow: Int
    ) {
        val currentImage = BaseImageRemediationExtractor.extractImageInfo(input)

        assertThat(currentImage, notNullValue())
        assertThat(
            currentImage,
            equalTo(
                BaseImageInfo(
                    name = expectedImage,
                    vulnerabilities = BaseImageVulnerabilities(
                        critical = expectedCritical,
                        high = expectedHigh,
                        medium = expectedMedium,
                        low = expectedLow
                    )
                )
            )
        )
    }

}
