package snyk.common.lsp

import java.util.Collections

object ScanState {
    const val SNYK_CODE = "code"
    val scanInProgress: MutableMap<String, Boolean> = Collections.synchronizedMap(mutableMapOf())
}
