package snyk.common.annotator

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.psi.PsiElement
import icons.SnykIcons
import javax.swing.Icon

// we only define a line marker provider so we can use the gutter icon settings to switch
// rendering of gutter icons on and off
class SnykLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName(): String {
        return "Snyk Security"
    }

    override fun getIcon(): Icon {
        return SnykIcons.TOOL_WINDOW
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }
}
