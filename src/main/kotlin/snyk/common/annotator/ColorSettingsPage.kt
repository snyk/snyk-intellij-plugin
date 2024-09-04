package snyk.common.annotator

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import icons.SnykIcons
import javax.swing.Icon

object SnykAnnotationAttributeKey {
    val unknown: TextAttributesKey = TextAttributesKey.createTextAttributesKey("Unknown Severity", TextAttributesKey.find("INFO_ATTRIBUTES"))
    val low: TextAttributesKey = TextAttributesKey.createTextAttributesKey("Low Severity", TextAttributesKey.find("INFO_ATTRIBUTES"))
    val medium: TextAttributesKey = TextAttributesKey.createTextAttributesKey("Medium Severity", TextAttributesKey.find("WARNING_ATTRIBUTES"))
    val high: TextAttributesKey = TextAttributesKey.createTextAttributesKey("High Severity", TextAttributesKey.find("ERRORS_ATTRIBUTES"))
    val critical: TextAttributesKey = TextAttributesKey.createTextAttributesKey("Critical Severity", TextAttributesKey.find("ERRORS_ATTRIBUTES"))
}

class SnykAnnotationColorSettingsPage : ColorSettingsPage {

    private val attributesDescriptors = arrayOf(
        AttributesDescriptor("Snyk Critical Issue", SnykAnnotationAttributeKey.critical),
        AttributesDescriptor("Snyk High Issue", SnykAnnotationAttributeKey.high),
        AttributesDescriptor("Snyk Medium Issue", SnykAnnotationAttributeKey.medium),
        AttributesDescriptor("Snyk Low Issue", SnykAnnotationAttributeKey.low),
        AttributesDescriptor("Snyk Unknown Issue", SnykAnnotationAttributeKey.unknown),
    )

    override fun getIcon(): Icon = SnykIcons.TOOL_WINDOW

    override fun getHighlighter(): SyntaxHighlighter = PlainSyntaxHighlighter()

    override fun getDemoText(): String =
        "This is a demo of a <snyk_critical_issue>Snyk Critical Issue</snyk_critical_issue>\n" +
            "This is a demo of a <snyk_high_issue>Snyk High Issue</snyk_high_issue>\n" +
            "This is a demo of a <snyk_medium_issue>Snyk Medium Issue</snyk_medium_issue>\n" +
            "This is a demo of a <snyk_low_issue>Snyk Low Issue</snyk_low_issue>\n" +
            "This is a demo of a <snyk_unknown_issue>Unknown High Issue</snyk_unknown_issue>\n"

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> =
        mapOf(
            "snyk_unknown_issue" to SnykAnnotationAttributeKey.unknown,
            "snyk_low_issue" to SnykAnnotationAttributeKey.low,
            "snyk_medium_issue" to SnykAnnotationAttributeKey.medium,
            "snyk_high_issue" to SnykAnnotationAttributeKey.high,
            "snyk_critical_issue" to SnykAnnotationAttributeKey.critical
        )

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = attributesDescriptors

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = "Snyk Colors"
}
