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
        val path = "C:\\Users\\username\\file.txt"
        val virtualFile = mockk<VirtualFile>()
        every { virtualFile.path } returns path

        assertEquals("file:///C:/Users/username/file.txt", virtualFile.toLanguageServerURI())
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
            "/Users/username/file.txt",
            "/Users/Username/file.txt",
            "/Users/user name/file.txt",
            "/Users/user name/hyphenated - folder/file.txt",
        )
        val inputUris = arrayOf(
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
        val expectedPaths = arrayOf(
            "C:\\Users\\username\\file.txt", // Valid path
            "c:\\Users\\username\\file.txt", // Valid path
            "C:\\Users\\username with spaces\\file.txt", // Valid path
            "C:\\Users\\username with hyphenated - spaces\\file.txt", // Valid path
            "C:\\Users\\username with \$peci@l characters\\file.txt", // Valid path
            "\\\\myserver\\shared folder\\file name with spaces \$peci@l%.txt"
        )

        val inputUris = arrayOf(
            "file:///C:/Users/username/file.txt", // Valid URI
            "file:///c:/Users/username/file.txt", // Valid URI
            "file:///C:/Users/username%20with%20spaces/file.txt", // Valid URI
            "file:///C:/Users/username%20with%20hyphenated%20-%20spaces/file.txt", // Valid URI
            "file:///C:/Users/username%20with%20\$peci@l%20characters/file.txt", // Valid URI
            "file://myserver/shared%20folder/file%20name%20with%20spaces%20\$peci@l%25.txt" // UNC
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
