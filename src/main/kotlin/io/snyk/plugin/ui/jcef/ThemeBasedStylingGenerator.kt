package io.snyk.plugin.ui.jcef

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.UIManager

/**
 * Centralized theme styling generator for JCEF HTML panels. Replaces CSS variable placeholders with
 * actual IDE theme values in a single pass.
 */
class ThemeBasedStylingGenerator {
  companion object {
    // Combined regex to match:
    // 1. var(--varName) or var(--varName, fallback) - CSS variable usage
    // 2. --varName: value - CSS variable declaration (to prepend font family)
    // Groups: 1=var usage varName, 2=var fallback, 3=declaration varName
    private val CSS_PATTERN = Regex("""var\(--([a-zA-Z0-9_-]+)(,[^)]*)?\)|--([a-zA-Z0-9_-]+):\s""")

    /**
     * Replace all CSS var() references and variable declarations in a single pass.
     *
     * @param varMap Map of variable names to replacement values for var() usage
     * @param declPrefixMap Map of variable names to prefix values for declarations (e.g.,
     *   --default-font: -> --default-font: "Font",)
     */
    internal fun replaceAllCssVars(
      html: String,
      varMap: Map<String, String>,
      declPrefixMap: Map<String, String>,
    ): String =
      CSS_PATTERN.replace(html) { matchResult ->
        val varUsageName = matchResult.groupValues[1]
        val varFallback = matchResult.groupValues[2]
        val declName = matchResult.groupValues[3]

        when {
          // var(--xxx) usage
          varUsageName.isNotEmpty() -> {
            varMap[varUsageName] ?: matchResult.value
          }
          // --xxx: declaration
          declName.isNotEmpty() -> {
            val prefix = declPrefixMap[declName]
            if (prefix != null) {
              "--$declName: $prefix"
            } else {
              matchResult.value
            }
          }
          else -> matchResult.value
        }
      }

    /**
     * Lighten or darken a hex color.
     *
     * @param hex "#RRGGBB"
     * @param factor -1.0 .. 1.0
     * > > 0 = lighten, < 0 = darken
     */
    fun adjustHexBrightness(hex: String, factor: Double): String {
      require(factor in -1.0..1.0) { "factor must be between -1.0 and 1.0" }

      val clean = hex.removePrefix("#")
      require(clean.length == 6) { "Expected 6-char hex like #RRGGBB" }

      fun adjust(comp: String): Int {
        val c = comp.toInt(16)
        val result =
          if (factor >= 0) {
            // move toward 255
            c + (255 - c) * factor
          } else {
            // move toward 0
            c + c * factor
          }
        return result.toInt().coerceIn(0, 255)
      }

      val r = adjust(clean.take(2))
      val g = adjust(clean.substring(2, 4))
      val b = adjust(clean.substring(4, 6))

      return "#%02X%02X%02X".format(r, g, b)
    }

    /**
     * Replaces var(--xxx) placeholders in HTML with actual theme values and adds theme classes to
     * body. Supports both VS Code-style (--vscode-xxx) and legacy (--text-color) variables.
     */
    fun replaceWithCustomStyles(htmlToReplace: String): String {
      var html = htmlToReplace
      val editorColorsManager = EditorColorsManager.getInstance()
      val isDarkTheme = editorColorsManager.isDarkEditor
      val darkenOrLightenFactor: Double = if (isDarkTheme) 1.0 else -1.0

      // All values computed fresh to support theme changes
      val bgColor = UIUtil.getPanelBackground().toHex()
      val fgColor = UIUtil.getLabelForeground().toHex()
      val dimmedFgColor = UIUtil.getLabelDisabledForeground().toHex()
      val borderColor = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground().toHex()
      val inputBgColor = UIUtil.getTextFieldBackground().toHex()
      val inputFgColor = UIUtil.getTextFieldForeground().toHex()
      val buttonBgColor = JBUI.CurrentTheme.Button.defaultButtonColorStart().toHex()
      val buttonFgColor =
        UIManager.getColor("Button.default.foreground")?.toHex()
          ?: UIManager.getColor("Button.foreground")?.toHex()
          ?: "#ffffff"
      val linkColor = JBUI.CurrentTheme.Link.Foreground.ENABLED.toHex()
      val fontFamily = JBUI.Fonts.label().asPlain().family
      val fontSize = JBFont.regular().size
      val scrollbarColor = UIManager.getColor("ScrollBar.thumbColor")?.toHex() ?: "#424242"
      val focusBorderColor = UIManager.getColor("Component.focusColor")?.toHex() ?: "#007acc"
      val infoBgColor = JBUI.CurrentTheme.Banner.INFO_BACKGROUND.toHex()
      val infoBorderColor = JBUI.CurrentTheme.Banner.INFO_BORDER_COLOR.toHex()
      val sectionBackground =
        adjustHexBrightness(UIUtil.getListBackground().toHex(), 0.05 * darkenOrLightenFactor)
      val editorUiTheme = editorColorsManager.schemeForCurrentUITheme
      val editorBackground =
        editorUiTheme.getColor(EditorColors.GUTTER_BACKGROUND)?.toHex()
          ?: editorUiTheme.defaultBackground.toHex()
      val globalScheme = editorColorsManager.globalScheme
      val tearLineColor =
        globalScheme.getColor(ColorKey.find("TEARLINE_COLOR"))?.toHex() ?: scrollbarColor
      val isHighContrast = globalScheme.name.contains("High contrast", ignoreCase = true)
      val successColor = JBUI.CurrentTheme.Banner.SUCCESS_BORDER_COLOR.toHex()
      val errorColor = JBUI.CurrentTheme.Banner.ERROR_BORDER_COLOR.toHex()
      val warningColor = JBUI.CurrentTheme.IconBadge.WARNING.toHex()
      val treeBackground = JBUI.CurrentTheme.Tree.BACKGROUND.toHex()
      val treeForeground = JBUI.CurrentTheme.Tree.FOREGROUND.toHex()
      val tabUnderline = JBUI.CurrentTheme.DefaultTabs.underlineColor().toHex()
      val tabBackground = JBUI.CurrentTheme.DefaultTabs.background().toHex()
      val labelColor = JBUI.CurrentTheme.Label.foreground().toHex()
      val labelBgColor = UIUtil.getLabelBackground().toHex()
      val checkboxBgColor = UIManager.getColor("CheckBox.background")?.toHex() ?: inputBgColor
      val checkboxFgColor = UIManager.getColor("CheckBox.foreground")?.toHex() ?: fgColor
      val checkboxSelectColor = UIManager.getColor("CheckBox.select")?.toHex() ?: buttonBgColor
      val sideBarBgColor = treeBackground
      val treeIndentGuideColor = borderColor
      val badgeBgColor = UIManager.getColor("Badge.background")?.toHex() ?: buttonBgColor
      val badgeFgColor = UIManager.getColor("Badge.foreground")?.toHex() ?: buttonFgColor
      val listSelectionBgColor =
        UIManager.getColor("List.selectionBackground")?.toHex() ?: buttonBgColor
      val listSelectionFgColor = UIManager.getColor("List.selectionForeground")?.toHex() ?: fgColor
      val listHoverBgColor =
        UIManager.getColor("List.hoverBackground")?.toHex()
          ?: adjustHexBrightness(bgColor, 0.1 * darkenOrLightenFactor)

      // Build variable map for single-pass replacement
      val varMap =
        mapOf(
          // VS Code style variables (settings panel)
          "vscode-font-family" to "'$fontFamily', system-ui, -apple-system, sans-serif",
          "vscode-font-size" to "${fontSize}px",
          "vscode-editor-background" to bgColor,
          "vscode-foreground" to fgColor,
          "vscode-editor-foreground" to fgColor,
          "vscode-input-background" to inputBgColor,
          "vscode-input-foreground" to inputFgColor,
          "vscode-input-border" to borderColor,
          "vscode-button-background" to buttonBgColor,
          "vscode-button-foreground" to buttonFgColor,
          "vscode-focusBorder" to focusBorderColor,
          "vscode-checkbox-background" to checkboxBgColor,
          "vscode-checkbox-foreground" to checkboxFgColor,
          "vscode-checkbox-selectBackground" to checkboxSelectColor,
          "vscode-checkbox-border" to borderColor,
          "vscode-scrollbarSlider-background" to scrollbarColor,
          "vscode-scrollbarSlider-hoverBackground" to scrollbarColor,
          "vscode-scrollbarSlider-activeBackground" to scrollbarColor,
          "vscode-panel-border" to borderColor,
          "vscode-sideBar-background" to sideBarBgColor,
          "vscode-tree-indentGuidesStroke" to treeIndentGuideColor,
          "vscode-badge-background" to badgeBgColor,
          "vscode-badge-foreground" to badgeFgColor,
          "vscode-list-activeSelectionBackground" to listSelectionBgColor,
          "vscode-list-activeSelectionForeground" to listSelectionFgColor,
          "vscode-list-hoverBackground" to listHoverBgColor,
          "vscode-editor-inactiveSelectionBackground" to sectionBackground,
          "vscode-inputValidation-infoBackground" to infoBgColor,
          "vscode-inputValidation-infoForeground" to fgColor,
          "vscode-inputValidation-infoBorder" to infoBorderColor,
          "vscode-textBlockQuote-background" to infoBgColor,
          "vscode-textBlockQuote-border" to infoBorderColor,
          "vscode-descriptionForeground" to dimmedFgColor,
          "vscode-inputValidation-errorBackground" to errorColor,
          "vscode-inputValidation-errorBorder" to errorColor,
          // Legacy variables
          "main-font-size" to getRelativeFontSize(fontSize),
          "text-color" to fgColor,
          "dimmed-text-color" to dimmedFgColor,
          "background-color" to bgColor,
          "ide-background-color" to bgColor,
          "border-color" to borderColor,
          "horizontal-border-color" to borderColor,
          "link-color" to linkColor,
          "example-line-added-color" to successColor,
          "example-line-removed-color" to errorColor,
          "data-flow-body-color" to treeBackground,
          "tab-item-github-icon-color" to treeForeground,
          "scrollbar-thumb-color" to tearLineColor,
          "tab-item-hover-color" to tabUnderline,
          "tabs-bottom-color" to tabBackground,
          "editor-color" to inputBgColor,
          "label-color" to labelColor,
          "container-background-color" to editorBackground,
          "button-background-color" to buttonBgColor,
          "button-text-color" to fgColor,
          "input-border" to borderColor,
          "disabled-background-color" to borderColor,
          "warning-background" to warningColor,
          "warning-text" to labelBgColor,
          "code-background-color" to editorBackground,
          "circle-color" to linkColor,
          // Fallback HTML variables
          "default-font" to "'$fontFamily', system-ui, -apple-system, sans-serif",
          "section-background-color" to sectionBackground,
          "input-background-color" to inputBgColor,
          "focus-color" to focusBorderColor,
        )

      // Declaration prefix map for CSS variable declarations (e.g., --default-font: sans-serif ->
      // --default-font: "Font", sans-serif)
      val declPrefixMap = mapOf("default-font" to "\"$fontFamily\", ")

      // Single-pass replacement of all CSS variables and declarations
      html = replaceAllCssVars(html, varMap, declPrefixMap)

      // Theme classes on body
      val contrast = if (isHighContrast) "high-contrast" else ""
      val theme = if (isDarkTheme) "dark" else "light"
      val lineWithBody = html.lines().find { it.contains("<body") }
      if (lineWithBody != null) {
        val modifiedLineWithBody =
          if (lineWithBody.contains("class")) {
            lineWithBody.replace("class", "class=\"$contrast $theme ")
          } else {
            lineWithBody.replace("<body", "<body class=\"$contrast $theme \"")
          }
        html = html.replace(lineWithBody, modifiedLineWithBody)
      }
      return html
    }

    /**
     * Utility function to scale JBFonts appropriately for use in HTML elements. JBFont defaults
     * will be environment specific.
     *
     * @see https://plugins.jetbrains.com/docs/intellij/typography.html#ide-font
     */
    internal fun getRelativeFontSize(inputFontSizePt: Int): String {
      val targetSizePx = 10
      val startingFontSizePt = if (inputFontSizePt > 0) inputFontSizePt else JBFont.regular().size
      val pxToPtMultiplier = 72.0 / 96.0
      val targetSizePt = targetSizePx * pxToPtMultiplier
      return String.format("%.3frem", targetSizePt / startingFontSizePt)
    }
  }
}
