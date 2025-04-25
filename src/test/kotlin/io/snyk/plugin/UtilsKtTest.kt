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

        assertEquals("file:/$path", virtualFile.toLanguageServerURI())

        uri = "file:///$path"
        virtualFile = mockk<VirtualFile>()
        every { virtualFile.url } returns uri

        assertEquals("file:/$path", virtualFile.toLanguageServerURI())
    }

    @Test
    fun isAdditionalParametersValid() {
        assertFalse(isAdditionalParametersValid("-d"))
        assertTrue(isAdditionalParametersValid("asdf"))
    }

    @Test
    fun toFilePathString() {

        // Windows files
        var pathsToTest = arrayOf(
            "C:\\Users\\username\\file.txt", // Valid path with Windows style separators
            "C:/Users/username/file.txt", // Valid path with Unix style separators
            "C:/Users/./username/../username/file.txt", // valid path with extra relative sub paths
            "file:///C:/Users/username/file.txt", // Valid path with scheme
            "file:/C:/Users/username/file.txt", // Valid path with scheme
            "file:///C:/Users/./username/../username/file.txt", // Valid path with scheme and extra relative sub paths
            "file://C:/Users/username/file.txt", // Invalid path with scheme.
        )

        var expectedPath = "C:/Users/username/file.txt" // Note we're deliberately testing for capitalization.
        var expectedUri = "file:///C:/Users/username/file.txt"

        for (path in pathsToTest) {
            assertEquals("Testing path $path normalization", expectedPath, path.toFilePathString())
            assertEquals("Testing path $path URI conversion", expectedUri, path.toFileURIString())
        }

        // Unix style files
        pathsToTest = arrayOf(
            "\\users\\username\\file.txt", // Valid path with Windows style separators
            "/users/username/file.txt", // Valid path with Unix style separators
            "/users/./username/../username/file.txt", // valid path with extra relative sub paths
            "file:///users/username/file.txt", // Valid path with scheme
            "file:/users/username/file.txt", // Valid path with scheme
            "file:///users/./username/../username/file.txt", // Valid path with scheme and extra relative sub paths
            "file://users/username/file.txt", // Invalid path with scheme.
        )

        expectedPath = "/users/username/file.txt"
        expectedUri = "file:///users/username/file.txt"

        for (path in pathsToTest) {
            assertEquals("Testing path $path normalization", expectedPath, path.toFilePathString())
            assertEquals("Testing path $path URI conversion", expectedUri, path.toFileURIString())
        }
    }
}
