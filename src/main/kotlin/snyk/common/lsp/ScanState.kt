package snyk.common.lsp

import com.intellij.openapi.vfs.VirtualFile
import snyk.common.ProductType
import java.util.concurrent.ConcurrentHashMap

object ScanState {
    val scanInProgress: MutableMap<ScanInProgressKey, Boolean> = ConcurrentHashMap()
}

data class ScanInProgressKey(val folderPath: VirtualFile, val productType: ProductType)
