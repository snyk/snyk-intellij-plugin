package io.snyk.plugin

import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
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
    fun toLanguageServerURL() {
        val path = "C:/Users/username/file.txt"
        var uri = "file://$path"
        var virtualFile = mockk<VirtualFile>()
        every { virtualFile.url } returns uri

        assertEquals("file:///$path", virtualFile.toLanguageServerURI())

        uri = "file:///$path"
        virtualFile = mockk<VirtualFile>()
        every { virtualFile.url } returns uri

        assertEquals("file:///$path", virtualFile.toLanguageServerURI())
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
        val expectedPaths = arrayOf(
            "/Users/username/file.txt", // Valid path
            "/Users/user name/file.txt", // Valid path
        )
        val inputUris = arrayOf(
            "file:///Users/username/file.txt", // Invalid URI
            "file:///Users/user%20name/file.txt", // Invalid URI
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
        val expectedPaths = arrayOf(
            "C:\\Users\\username\\file.txt", // Valid path
            "C:\\Users\\username with spaces\\file.txt", // Valid path
            "C:\\Users\\username with \$peci@l characters\\file.txt", // Valid path
            "\\\\my server\\shared folder\\file name with spaces & special%.txt"
        )

        val inputUris = arrayOf(
            "file:///C:/Users/username/file.txt", // Valid URI
            "file:///C:/Users/username%20with%20spaces/file.txt", // Valid URI
            "file:///C:/Users/username%20with%20%24peci@l%20characters/file.txt", // Valid URI
            "file://my%20server/shared%20folder/file%20name%20with%20spaces%20%26%20special%25.txt" // UNC
        )

        var i = 0
        for (uri in inputUris) {
            val actualPath = uri.fromUriToPath().toString()
            assertEquals("Testing $uri to path conversion", expectedPaths[i], actualPath)
            assertEquals("Testing $actualPath to uri conversion", uri, actualPath.fromPathToUriString())
            i++
        }
    }
}
