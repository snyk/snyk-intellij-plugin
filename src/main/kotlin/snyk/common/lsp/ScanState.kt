package snyk.common.lsp

import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap
import snyk.common.ProductType

object ScanState {
  val scanInProgress: MutableMap<ScanInProgressKey, Boolean> = ConcurrentHashMap()
}

data class ScanInProgressKey(val folderPath: VirtualFile, val productType: ProductType)
