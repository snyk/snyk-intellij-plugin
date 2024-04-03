package snyk.trust

import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.junit.Test
import snyk.InMemoryFsRule

class WorkspaceTrustServiceTest {

    @JvmField
    @Rule
    val memoryFs = InMemoryFsRule()

    @Test
    fun `isAncestor should return true for itself`() {
        val absoluteSimpleDir = memoryFs.fs.getPath("/opt/projects/simple")
        val relativeSimpleDir = memoryFs.fs.getPath("projects/simple")

        assertTrue(absoluteSimpleDir.isAncestor(absoluteSimpleDir))
        assertTrue(relativeSimpleDir.isAncestor(relativeSimpleDir))
    }

    @Test
    fun `isAncestor should return true for inner folder inside of outer`() {
        val absoluteOuterDir = memoryFs.fs.getPath("/opt/projects/outer")
        val absoluteInnerDir = memoryFs.fs.getPath("/opt/projects/outer/inner")
        val relativeOuterDir = memoryFs.fs.getPath("projects/outer")
        val relativeInnerDir = memoryFs.fs.getPath("projects/outer/inner")

        assertTrue(absoluteOuterDir.isAncestor(absoluteInnerDir))
        assertTrue(relativeOuterDir.isAncestor(relativeInnerDir))
    }

    @Test
    fun `isAncestor should return true for inner folder with more than one level inside of outer`() {
        val absoluteOuterDir = memoryFs.fs.getPath("/opt/projects/outer")
        val absoluteInnerDir = memoryFs.fs.getPath("/opt/projects/outer/level1/level2/level3/inner")
        val relativeOuterDir = memoryFs.fs.getPath("projects/outer")
        val relativeInnerDir = memoryFs.fs.getPath("projects/outer/level1/level2/level3/inner")

        assertTrue(absoluteOuterDir.isAncestor(absoluteInnerDir))
        assertTrue(relativeOuterDir.isAncestor(relativeInnerDir))
    }

    @Test
    fun `isAncestor should return false for outer folder`() {
        val absoluteOuterDir = memoryFs.fs.getPath("/opt/projects/outer")
        val absoluteInnerDir = memoryFs.fs.getPath("/opt/projects/outer/inner")
        val relativeOuterDir = memoryFs.fs.getPath("projects/outer")
        val relativeInnerDir = memoryFs.fs.getPath("projects/outer/inner")

        assertFalse(absoluteInnerDir.isAncestor(absoluteOuterDir))
        assertFalse(relativeInnerDir.isAncestor(relativeOuterDir))
    }

    @Test
    fun `isAncestor should return false for folders on different levels`() {
        val absoluteFirstDir = memoryFs.fs.getPath("/opt/projects/first")
        val absoluteSecondDir = memoryFs.fs.getPath("/opt/projects/second")
        val relativeFirstDir = memoryFs.fs.getPath("projects/first")
        val relativeSecondDir = memoryFs.fs.getPath("projects/second")

        assertFalse(absoluteFirstDir.isAncestor(absoluteSecondDir))
        assertFalse(absoluteSecondDir.isAncestor(absoluteFirstDir))
        assertFalse(relativeFirstDir.isAncestor(relativeSecondDir))
        assertFalse(relativeSecondDir.isAncestor(relativeFirstDir))
    }
}
