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

        assertEquals("file:///$path", virtualFile.toLanguageServerURL())

        uri = "file:///$path"
        virtualFile = mockk<VirtualFile>()
        every { virtualFile.url } returns uri

        assertEquals("file:///$path", virtualFile.toLanguageServerURL())
    }

    @Test
    fun toVirtualFileURL() {
        val path = "C:/Users/username/file.txt"
        var testURI = "file:///$path"
        assertEquals("file://$path", testURI.toVirtualFileURL())
        testURI = "file://$path"
        assertEquals("file://$path", testURI.toVirtualFileURL())
    }

    @Test
    fun isWindowsURI() {
        var uri = "file:///C:/Users/username/file.txt"
        assertTrue(uri.isWindowsURI())
        uri = "file://C:/Users/username/file.txt"
        assertTrue(uri.isWindowsURI())
        assertFalse("C:\\Users\\username\\file.txt".isWindowsURI())
    }

    @Test
    fun urlContainsDriveLetter() {
        var uri = "file://C:/Users/username/file.txt"
        var virtualFile = mockk<VirtualFile>()
        every { virtualFile.url } returns uri
        assertTrue(virtualFile.urlContainsDriveLetter())

        uri = "file://Users/username/file.txt"
        virtualFile = mockk<VirtualFile>()
        every { virtualFile.url } returns uri

        assertFalse(virtualFile.urlContainsDriveLetter())
    }
}
