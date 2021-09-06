package snyk.container

import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.notNullValue
import org.junit.Assert.assertThat
import org.junit.Test

class BaseImageRemediationExtractorTest {

    @Test
    fun extractImageInfo() {
        val input = """
Base Image  Vulnerabilities  Severity\nnginx:1.17  150              17 critical, 35 high, 26 medium, 72 low\n
        """

        val currentImage = BaseImageRemediationExtractor.extractImageInfo(input)

        assertThat(currentImage, notNullValue())
        assertThat(
            currentImage, equalTo(
                BaseImageInfo(
                    name = "nginx:1.17",
                    vulnerabilities = BaseImageVulnerabilities(
                        critical = 17,
                        high = 35,
                        medium = 26,
                        low = 72
                    )
                )
            )
        )
    }
}
