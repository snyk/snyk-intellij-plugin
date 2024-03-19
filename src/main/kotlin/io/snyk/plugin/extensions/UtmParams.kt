
package io.snyk.plugin.extensions

import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.util.xmlb.annotations.Attribute

class UtmParams : AbstractExtensionPointBean() {
    @Attribute("source")
    var source: String? = null

    // Add other fields...
}
