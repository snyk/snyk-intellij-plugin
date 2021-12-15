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

        val currentImage = BaseImageRemediationExtractor.extractImageInfo(input)

        assertThat(currentImage, notNullValue())
        assertThat(
            currentImage,
            equalTo(
                BaseImageInfo(
                    name = "nginx:1.16.0",
                    vulnerabilities = BaseImageVulnerabilities(
                        critical = 23,
                        high = 44,
                        medium = 38,
                        low = 83
                    )
                )
            )
        )
    }
}
