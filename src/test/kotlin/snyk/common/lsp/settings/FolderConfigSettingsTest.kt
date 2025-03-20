package snyk.common.lsp.settings

import com.google.gson.Gson
import com.intellij.testFramework.LightPlatformTestCase

import snyk.common.lsp.FolderConfig

class FolderConfigSettingsTest : LightPlatformTestCase() {

    private lateinit var gson: Gson
    private lateinit var folderConfigSettings: FolderConfigSettings

    override fun setUp() {
        super.setUp()
        gson = Gson()
        folderConfigSettings = FolderConfigSettings()
    }

    fun testGetAdditionalParamsShouldReturnAllProjectsWhenConfigured() {
        val folderPath = "/test/folder"
        val folderConfig = FolderConfig(
            folderPath = folderPath,
            baseBranch = "testBranch",
            additionalParameters = listOf("--all-projects")
        )
        folderConfigSettings.addFolderConfig(folderConfig)

        val result = folderConfigSettings.getAdditionalParams(folderPath)

        assertEquals("--all-projects", result)
    }
}
