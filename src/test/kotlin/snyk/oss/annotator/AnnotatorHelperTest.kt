package snyk.oss.annotator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotatorHelperTest {

    @Test
    fun `test isFileSupported`() {
        assertTrue(AnnotatorHelper.isFileSupported("xyz/pom.xml"))
        assertFalse(AnnotatorHelper.isFileSupported("xyz/pomGrenade.xml"))
    }
}


