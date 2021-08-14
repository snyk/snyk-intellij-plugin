package snyk.advisor

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PackageNameProviderTest : BasePlatformTestCase() {

    private fun getResourceAsString(resourceName: String): String = javaClass.classLoader
        .getResource(resourceName)!!.readText(Charsets.UTF_8)

    fun testPythonPackageNames() {
        myFixture.configureByText(
            "requirements.txt",
            getResourceAsString("advisor-build-files/requirements.txt")
        )
        val packageNameProvider = PackageNameProvider(myFixture.editor)

        fun assertPackageName(name: String?, lineNumber: Int) {
            assertEquals(
                Pair(name, AdvisorPackageManager.PYTHON),
                packageNameProvider.getPackageName(lineNumber)
            )
        }

        // option is not a name
        assertPackageName(null, 0)
        // version specifier right after
        assertPackageName("piper", 1)
        // number in name
        assertPackageName("subprocess32", 12)
        // comment is not a name
        assertPackageName(null, 14)
        // version specifier after space and comment in the end
        assertPackageName("docopt", 52)
        // file path is not a name
        assertPackageName(null, 62)
        // url is not a name
        assertPackageName(null, 63)
        // name with line continuation
        assertPackageName("rejected", 67)
        // no name after line continuation
        assertPackageName(null, 68)
        // no name for local package
        assertPackageName(null, 72)
        // name for version range
        assertPackageName("posix_ipc", 73)
        // no name for windows path
        assertPackageName(null, 99)
        // out of range
        assertPackageName(null, -100)
        assertPackageName(null, 999999)
    }

    fun testNpmPackageNames() {
        myFixture.configureByText(
            "package.json",
            getResourceAsString("advisor-build-files/package.json")
        )
        val packageNameProvider = PackageNameProvider(myFixture.editor)

        fun assertPackageName(name: String?, lineNumber: Int) {
            assertEquals(
                Pair(name, AdvisorPackageManager.NPM),
                packageNameProvider.getPackageName(lineNumber)
            )
        }

        // out of "dependencies" node
        assertPackageName(null, 1)
        // version specifier right after
        assertPackageName("adm-zip", 16)
        // empty line between names
        assertPackageName(null, 22)
        // no spaces before name
        assertPackageName("ejs-locals", 25)
        // out of range
        assertPackageName(null, -100)
        assertPackageName(null, 999999)
    }

    fun testNotSupportedPackageMangerFile() {
        myFixture.configureByText(
            "not-supported-package-manager.file",
            """--extra-index-url https://pypi.fury.io/szxGyDm9EBpkyVFVy7qR/snyk-org/
               piper==1.5.9
               pika==0.10.0
               pymongo
               "dependencies": {
                 "adm-zip": "0.4.7",
                 "body-parser": "1.9.0",
                 "cfenv": "^1.0.4"
               }
            """.trimIndent()
        )
        val packageNameProvider = PackageNameProvider(myFixture.editor)

        assertEquals(
            null,
            packageNameProvider.getPackageName(3)
        )
    }
}
