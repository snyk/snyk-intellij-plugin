package snyk.sdk

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import snyk.common.lsp.LsSdk

class SdkHelper {
    companion object {
        fun getSdks(project: Project): List<LsSdk> {
            val list: MutableList<LsSdk> = mutableListOf()
            val modules = ModuleManager.getInstance(project).modules
            for (module in modules) {
                val moduleSdk = ModuleRootManager.getInstance(module).sdk
                addSdkToList(moduleSdk, list)
            }
            return list.toList()
        }

        private fun addSdkToList(
            sdk: Sdk?,
            list: MutableList<LsSdk>
        ) {
            if (sdk != null && sdk.homeDirectory != null) {
                list.add(LsSdk(sdk.sdkType.name, FileUtil.toSystemDependentName(sdk.homeDirectory!!.path)))
            }
        }
    }
}
