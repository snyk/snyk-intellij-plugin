package snyk.sdk

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import snyk.common.lsp.LsSdk

class SdkHelperTest {

  private lateinit var project: Project
  private lateinit var moduleManager: ModuleManager

  @Before
  fun setUp() {
    project = mockk(relaxed = true)
    moduleManager = mockk(relaxed = true)

    mockkStatic(ModuleManager::class)
    every { ModuleManager.getInstance(project) } returns moduleManager

    mockkStatic(ModuleRootManager::class)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  private fun createModuleWithSdk(sdkTypeName: String, sdkHomePath: String): Module {
    val module = mockk<Module>(relaxed = true)
    val moduleRootManager = mockk<ModuleRootManager>(relaxed = true)
    val sdk = mockk<Sdk>(relaxed = true)
    val sdkType = mockk<SdkType>(relaxed = true)
    val homeDir = mockk<VirtualFile>(relaxed = true)

    every { ModuleRootManager.getInstance(module) } returns moduleRootManager
    every { moduleRootManager.sdk } returns sdk
    every { sdk.sdkType } returns sdkType
    every { sdkType.name } returns sdkTypeName
    every { sdk.homeDirectory } returns homeDir
    every { homeDir.path } returns sdkHomePath
    return module
  }

  private fun createModuleWithoutSdk(): Module {
    val module = mockk<Module>(relaxed = true)
    val moduleRootManager = mockk<ModuleRootManager>(relaxed = true)

    every { ModuleRootManager.getInstance(module) } returns moduleRootManager
    every { moduleRootManager.sdk } returns null
    return module
  }

  @Test
  fun `getSdks returns deduplicated list when multiple modules share the same sdk`() {
    val module1 = createModuleWithSdk("Python SDK", "/usr/bin/python3")
    val module2 = createModuleWithSdk("Python SDK", "/usr/bin/python3")
    val module3 = createModuleWithSdk("Python SDK", "/usr/bin/python3")

    every { moduleManager.modules } returns arrayOf(module1, module2, module3)

    val result = SdkHelper.getSdks(project)

    assertEquals(1, result.size)
    assertEquals(LsSdk("Python SDK", FileUtil.toSystemDependentName("/usr/bin/python3")), result[0])
  }

  @Test
  fun `getSdks preserves distinct sdks`() {
    val module1 = createModuleWithSdk("Python SDK", "/usr/bin/python3")
    val module2 = createModuleWithSdk("Java SDK", "/usr/lib/jvm/java-17")

    every { moduleManager.modules } returns arrayOf(module1, module2)

    val result = SdkHelper.getSdks(project)

    assertEquals(2, result.size)
    assertTrue(
      result.contains(LsSdk("Java SDK", FileUtil.toSystemDependentName("/usr/lib/jvm/java-17")))
    )
    assertTrue(
      result.contains(LsSdk("Python SDK", FileUtil.toSystemDependentName("/usr/bin/python3")))
    )
  }

  @Test
  fun `getSdks deduplicates across many modules simulating large monorepo`() {
    val modules =
      (1..1000).map { createModuleWithSdk("Java SDK", "/usr/lib/jvm/java-17") }.toTypedArray()

    every { moduleManager.modules } returns modules

    val result = SdkHelper.getSdks(project)

    assertEquals(1, result.size)
  }

  @Test
  fun `getSdks skips modules with null sdk`() {
    val module1 = createModuleWithSdk("Python SDK", "/usr/bin/python3")
    val module2 = createModuleWithoutSdk()

    every { moduleManager.modules } returns arrayOf(module1, module2)

    val result = SdkHelper.getSdks(project)

    assertEquals(1, result.size)
  }

  @Test
  fun `getSdks returns empty list when no modules have sdks`() {
    val module1 = createModuleWithoutSdk()
    val module2 = createModuleWithoutSdk()

    every { moduleManager.modules } returns arrayOf(module1, module2)

    val result = SdkHelper.getSdks(project)

    assertEquals(0, result.size)
  }
}
