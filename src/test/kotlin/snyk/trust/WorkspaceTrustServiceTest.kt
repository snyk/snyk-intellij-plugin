package snyk.trust

import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
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

        assertThat(absoluteSimpleDir.isAncestor(absoluteSimpleDir), equalTo(true))
        assertThat(relativeSimpleDir.isAncestor(relativeSimpleDir), equalTo(true))
    }

    @Test
    fun `isAncestor should return true for inner folder inside of outer`() {
        val absoluteOuterDir = memoryFs.fs.getPath("/opt/projects/outer")
        val absoluteInnerDir = memoryFs.fs.getPath("/opt/projects/outer/inner")
        val relativeOuterDir = memoryFs.fs.getPath("projects/outer")
        val relativeInnerDir = memoryFs.fs.getPath("projects/outer/inner")

        assertThat(absoluteOuterDir.isAncestor(absoluteInnerDir), equalTo(true))
        assertThat(relativeOuterDir.isAncestor(relativeInnerDir), equalTo(true))
    }

    @Test
    fun `isAncestor should return true for inner folder with more than one level inside of outer`() {
        val absoluteOuterDir = memoryFs.fs.getPath("/opt/projects/outer")
        val absoluteInnerDir = memoryFs.fs.getPath("/opt/projects/outer/level1/level2/level3/inner")
        val relativeOuterDir = memoryFs.fs.getPath("projects/outer")
        val relativeInnerDir = memoryFs.fs.getPath("projects/outer/level1/level2/level3/inner")

        assertThat(absoluteOuterDir.isAncestor(absoluteInnerDir), equalTo(true))
        assertThat(relativeOuterDir.isAncestor(relativeInnerDir), equalTo(true))
    }

    @Test
    fun `isAncestor should return false for outer folder`() {
        val absoluteOuterDir = memoryFs.fs.getPath("/opt/projects/outer")
        val absoluteInnerDir = memoryFs.fs.getPath("/opt/projects/outer/inner")
        val relativeOuterDir = memoryFs.fs.getPath("projects/outer")
        val relativeInnerDir = memoryFs.fs.getPath("projects/outer/inner")

        assertThat(absoluteInnerDir.isAncestor(absoluteOuterDir), equalTo(false))
        assertThat(relativeInnerDir.isAncestor(relativeOuterDir), equalTo(false))
    }

    @Test
    fun `isAncestor should return false for folders on different levels`() {
        val absoluteFirstDir = memoryFs.fs.getPath("/opt/projects/first")
        val absoluteSecondDir = memoryFs.fs.getPath("/opt/projects/second")
        val relativeFirstDir = memoryFs.fs.getPath("projects/first")
        val relativeSecondDir = memoryFs.fs.getPath("projects/second")

        assertThat(absoluteFirstDir.isAncestor(absoluteSecondDir), equalTo(false))
        assertThat(absoluteSecondDir.isAncestor(absoluteFirstDir), equalTo(false))
        assertThat(relativeFirstDir.isAncestor(relativeSecondDir), equalTo(false))
        assertThat(relativeSecondDir.isAncestor(relativeFirstDir), equalTo(false))
    }
}
