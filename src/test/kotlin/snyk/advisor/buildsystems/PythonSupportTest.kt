package snyk.advisor.buildsystems

import org.junit.Test
import kotlin.test.assertEquals

class PythonSupportTest {

    @Test
    fun `parsing line of requirements_txt for package name`() {
        assertEquals(
                "some-py-package",
                PythonSupport.findPackageName("some-py-package")
        )
        assertEquals(
                null,
                PythonSupport.findPackageName("# some py comment")
        )
        assertEquals(
                "piper",
                PythonSupport.findPackageName("piper==1.5.9")
        )
        assertEquals(
                "subprocess32",
                PythonSupport.findPackageName("subprocess32")
        )
        assertEquals(
                "na_m-e",
                PythonSupport.findPackageName("na_m-e")
        )
        assertEquals(
                "docopt",
                PythonSupport.findPackageName("docopt == 0.6.1 # Version Matching.")
        )
        assertEquals(
                null,
                PythonSupport.findPackageName("-r other-requirements.txt")
        )
        assertEquals(
                null,
                PythonSupport.findPackageName("-r other-requirements.txt")
        )
        assertEquals(
                null,
                PythonSupport.findPackageName("https://www.com")
        )
        assertEquals(
                null,
                PythonSupport.findPackageName("./downloads/numpy-1.9.2-cp34-none-win32.whl")
        )
        assertEquals(
                null,
                PythonSupport.findPackageName("windows\\\\path")
        )
        assertEquals(
                null,
                PythonSupport.findPackageName("local-package @ file:///somewhere")
        )
        assertEquals(
                "posix_ipc",
                PythonSupport.findPackageName("posix_ipc ( >1.0.0, <2.00)")
        )
        assertEquals(
                "secure-pkg",
                PythonSupport.findPackageName("secure-pkg \\")
        )
    }
}
