package snyk.common.annotator

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.ui.JBColor
import icons.SnykIcons
import javax.swing.Icon

object SnykAnnotationAttributeKey {
    val snykAnnotationKey: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("Snyk Issue")


    init {
        snykAnnotationKey.apply {
            this.defaultAttributes.effectColor = JBColor.MAGENTA
            this.defaultAttributes.effectType = EffectType.WAVE_UNDERSCORE
        }
    }
}

class SnykAnnotationColorSettingsPage : ColorSettingsPage {

    private val attributesDescriptors = arrayOf(
        AttributesDescriptor("Snyk Issue", SnykAnnotationAttributeKey.snykAnnotationKey)
    )

    override fun getIcon(): Icon = SnykIcons.TOOL_WINDOW

    override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

    override fun getDemoText(): String =
        "This is a demo of <snyk_issue>a Snyk Issue</snyk_issue>"

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> =
        mapOf("snyk_issue" to SnykAnnotationAttributeKey.snykAnnotationKey)

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = attributesDescriptors

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Snyk Colors"
}
