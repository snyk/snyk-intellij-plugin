package io.snyk.plugin.ui.jcef

import com.google.gson.JsonParser
import org.junit.Test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue

class ExtractPatchTest {

    @Test
    fun `folderConfigPatch includes all fields except folderPath`() {
        val json = """
        {
            "folderPath": "/path/to/folder",
            "baseBranch": "main",
            "snykOssEnabled": true,
            "additionalEnv": "FOO=bar"
        }
        """.trimIndent()
        val entry = JsonParser.parseString(json).asJsonObject
        val patch = extractFolderConfigPatch(entry)

        assertEquals(3, patch.size)
        assertFalse("folderPath should be excluded", patch.containsKey("folderPath"))
        assertEquals("main", patch["baseBranch"])
        assertEquals(true, patch["snykOssEnabled"])
        assertEquals("FOO=bar", patch["additionalEnv"])
    }

    @Test
    fun `folderConfigPatch handles arrays`() {
        val json = """
        {
            "folderPath": "/path/to/folder",
            "additionalParameters": ["--all-projects", "--detection-depth=2"]
        }
        """.trimIndent()
        val entry = JsonParser.parseString(json).asJsonObject
        val patch = extractFolderConfigPatch(entry)

        assertEquals(1, patch.size)
        @Suppress("UNCHECKED_CAST")
        val params = patch["additionalParameters"] as List<Any?>
        assertEquals(2, params.size)
        assertEquals("--all-projects", params[0])
        assertEquals("--detection-depth=2", params[1])
    }

    @Test
    fun `folderConfigPatch preserves null for org-scope clear override`() {
        val json = """
        {
            "folderPath": "/path/to/folder",
            "baseBranch": "develop",
            "snykOssEnabled": null
        }
        """.trimIndent()
        val entry = JsonParser.parseString(json).asJsonObject
        val patch = extractFolderConfigPatch(entry)

        assertEquals(2, patch.size)
        assertEquals("develop", patch["baseBranch"])
        assertTrue("snykOssEnabled should be present", patch.containsKey("snykOssEnabled"))
        assertEquals(null, patch["snykOssEnabled"])
    }

    @Test
    fun `folderConfigPatch returns empty map for folderPath-only entry`() {
        val json = """{ "folderPath": "/path/to/folder" }"""
        val entry = JsonParser.parseString(json).asJsonObject
        val patch = extractFolderConfigPatch(entry)

        assertTrue("Patch should be empty", patch.isEmpty())
    }
}
