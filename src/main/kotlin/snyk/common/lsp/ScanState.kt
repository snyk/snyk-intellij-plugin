package snyk.common.lsp

import com.intellij.openapi.vfs.VirtualFile
import snyk.common.ProductType
import java.util.Collections

object ScanState {
    val scanInProgress: MutableMap<ScanInProgressKey, Boolean> = Collections.synchronizedMap(mutableMapOf())
}

data class ScanInProgressKey(val folderPath: VirtualFile, val productType: ProductType)
