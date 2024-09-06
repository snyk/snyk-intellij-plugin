package io.snyk.plugin.ui.jcef

import org.junit.Test
import junit.framework.TestCase.assertEquals

class DiffPatchTest {
    private  val originalFileContent = """
        /*
         * Copyright (c) 2014-2023 Bjoern Kimminich & the OWASP Juice Shop contributors.
         * SPDX-License-Identifier: MIT
         */

        import path = require('path')
        import { type Request, type Response } from 'express'

        import challengeUtils = require('../lib/challengeUtils')
        const challenges = require('../data/datacache').challenges

        module.exports = function servePremiumContent () {
          return (req: Request, res: Response) => {
            challengeUtils.solveIf(challenges.premiumPaywallChallenge, () => { return true })
            res.sendFile(path.resolve('frontend/dist/frontend/assets/private/JuiceShop_Wallpaper_1920x1080_VR.jpg'))
          }
        }
    """.trimIndent()

    private val responseDiff = """
        --- /Users/cata/git/playground/project-with-vulns
        +++ /Users/cata/git/playground/project-with-vulns-fixed
        @@ -4,9 +4,14 @@
         */

         import path = require('path')
        +import rateLimit = require('express-rate-limit')
         import { type Request, type Response } from 'express'

         import challengeUtils = require('../lib/challengeUtils')
        +const apiLimiter = rateLimit({
        +  windowMs: 15 * 60 * 1000, // 15 minutes
        +  max: 100, // limit each IP to 100 requests per windowMs
        +})
         const challenges = require('../data/datacache').challenges

         module.exports = function servePremiumContent () {
    """.trimIndent()

    @Test
    fun `test applying patch`() {
        val diffPatch = parseDiff(responseDiff)
        val patchedContent = applyPatch(originalFileContent, diffPatch)

        val expectedPatchedContent = """
            /*
             * Copyright (c) 2014-2023 Bjoern Kimminich & the OWASP Juice Shop contributors.
             * SPDX-License-Identifier: MIT
             */

            import path = require('path')
            import rateLimit = require('express-rate-limit')
            import { type Request, type Response } from 'express'

            import challengeUtils = require('../lib/challengeUtils')
            const apiLimiter = rateLimit({
            windowMs: 15 * 60 * 1000, // 15 minutes
            max: 100, // limit each IP to 100 requests per windowMs
            })
            const challenges = require('../data/datacache').challenges

            module.exports = function servePremiumContent () {
              return (req: Request, res: Response) => {
                challengeUtils.solveIf(challenges.premiumPaywallChallenge, () => { return true })
                res.sendFile(path.resolve('frontend/dist/frontend/assets/private/JuiceShop_Wallpaper_1920x1080_VR.jpg'))
              }
            }
        """.trimIndent()

        assertEquals(expectedPatchedContent, patchedContent)
    }

    private fun applyPatch(fileContent: String, diffPatch: DiffPatch): String {
        val lines = fileContent.lines().toMutableList()

        for (hunk in diffPatch.hunks) {
            var originalLineIndex = hunk.startLineOriginal - 1  // Convert to 0-based index

            for (change in hunk.changes) {
                when (change) {
                    is Change.Addition -> {
                        lines.add(originalLineIndex, change.line)
                        originalLineIndex++
                    }
                    is Change.Deletion -> {
                        if (originalLineIndex < lines.size && lines[originalLineIndex].trim() == change.line) {
                            lines.removeAt(originalLineIndex)
                        }
                    }
                    is Change.Context -> {
                        originalLineIndex++  // Move past unchanged context lines
                    }
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun parseDiff(diff: String): DiffPatch {
        val lines = diff.lines()
        val originalFile = lines.first { it.startsWith("---") }.substringAfter("--- ")
        val fixedFile = lines.first { it.startsWith("+++") }.substringAfter("+++ ")

        val hunks = mutableListOf<Hunk>()
        var currentHunk: Hunk? = null
        val changes = mutableListOf<Change>()

        for (line in lines) {
            when {
                line.startsWith("@@") -> {
                    // Parse hunk header (e.g., @@ -4,9 +4,14 @@)
                    val hunkHeader = line.substringAfter("@@ ").substringBefore(" @@").split(" ")
                    val original = hunkHeader[0].substring(1).split(",")
                    val fixed = hunkHeader[1].substring(1).split(",")

                    val startLineOriginal = original[0].toInt()
                    val numLinesOriginal = original.getOrNull(1)?.toInt() ?: 1
                    val startLineFixed = fixed[0].toInt()
                    val numLinesFixed = fixed.getOrNull(1)?.toInt() ?: 1

                    if (currentHunk != null) {
                        hunks.add(currentHunk.copy(changes = changes.toList()))
                        changes.clear()
                    }
                    currentHunk = Hunk(
                        startLineOriginal = startLineOriginal,
                        numLinesOriginal = numLinesOriginal,
                        startLineFixed = startLineFixed,
                        numLinesFixed = numLinesFixed,
                        changes = emptyList()
                    )
                }

                line.startsWith("---") || line.startsWith("+++") -> {
                    // Skip file metadata lines (--- and +++)
                    continue
                }

                line.startsWith("-") -> changes.add(Change.Deletion(line.substring(1).trim()))
                line.startsWith("+") -> changes.add(Change.Addition(line.substring(1).trim()))
                else -> changes.add(Change.Context(line.trim()))
            }
        }

        // Add the last hunk
        if (currentHunk != null) {
            hunks.add(currentHunk.copy(changes = changes.toList()))
        }

        return DiffPatch(
            originalFile = originalFile,
            fixedFile = fixedFile,
            hunks = hunks
        )
    }
}
