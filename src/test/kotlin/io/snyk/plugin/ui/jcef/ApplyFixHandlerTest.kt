package io.snyk.plugin.ui.jcef

import org.junit.Test
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DiffPatchTest : BasePlatformTestCase(){
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
        val applyFixHandler = ApplyFixHandler(project)

        val diffPatch = applyFixHandler.parseDiff(responseDiff)
        val patchedContent = applyFixHandler.applyPatch(originalFileContent, diffPatch)

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
}
