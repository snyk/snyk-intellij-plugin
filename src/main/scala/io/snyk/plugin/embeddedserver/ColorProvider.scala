package io.snyk.plugin.embeddedserver

import java.awt.Color

import com.intellij.util.ui.UIUtil

object ColorProvider {
  implicit class RichColor(val c: Color) extends AnyVal {
    def hsb: (Float, Float, Float) = {
      if(c == null) (0f, 0f, 0f) else {
        val arr = Color.RGBtoHSB(c.getRed, c.getGreen, c.getBlue, null)
        (arr(0), arr(1), arr(2))
      }
    }
    def hue: Float = hsb._1
    def saturation: Float = hsb._2
    def brightness: Float = hsb._3

    def hexRepr: String =
      if(c == null) "#FF0000" else f"#${c.getRed}%02x${c.getGreen}%02x${c.getBlue}%02x"
  }

  def intellij: ColorProvider = IntelliJColorProvider
  def mockDarkula: ColorProvider = MockDarkulaColorProvider
}

import ColorProvider._


trait ColorProvider {
  def linkColor: Color
  def panelBgColor: Color
  def toolTipColor: Color
  def buttonSelectColor: Color
  def separatorFgColor: Color
  def separatorBgColor: Color
  def separatorShadowColor: Color
  def separatorHighlightColor: Color
  def boundsColor: Color
  def labelFgColor: Color
  def tabbedPaneBgColor: Color
  def activeTextColor: Color
  def inactiveTextColor: Color

  //TODO: decent macro solution is needed - this is WAY too fragile
  def toMap: Map[String, Color] = Map(
    "linkColor"               -> linkColor,
    "panelBgColor"            -> panelBgColor,
    "toolTipColor"            -> toolTipColor,
    "buttonSelectColor"       -> buttonSelectColor,
    "separatorFgColor"        -> separatorFgColor,
    "separatorBgColor"        -> separatorBgColor,
    "separatorShadowColor"    -> separatorShadowColor,
    "separatorHighlightColor" -> separatorHighlightColor,
    "boundsColor"             -> boundsColor,
    "labelFgColor"            -> labelFgColor,
    "tabbedPaneBgColor"       -> tabbedPaneBgColor,
    "activeTextColor"         -> activeTextColor,
    "inactiveTextColor"       -> inactiveTextColor
  )
}

private object IntelliJColorProvider extends ColorProvider {
  def bgBrightness = UIUtil.getPanelBackground.brightness
  override def linkColor = if(bgBrightness > 0.5f) new Color(75, 69, 161) else new Color(88, 157, 246)
  override def panelBgColor            : Color = UIUtil.getPanelBackground
  override def toolTipColor            : Color = UIUtil.getToolTipForeground
  override def buttonSelectColor       : Color = UIUtil.getButtonSelectColor
  override def separatorFgColor        : Color = UIUtil.getSeparatorForeground
  override def separatorBgColor        : Color = UIUtil.getSeparatorBackground
  override def separatorShadowColor    : Color = UIUtil.getSeparatorShadow
  override def separatorHighlightColor : Color = UIUtil.getSeparatorHighlight
  override def boundsColor             : Color = UIUtil.getBoundsColor
  override def labelFgColor            : Color = UIUtil.getLabelForeground
  override def tabbedPaneBgColor       : Color = UIUtil.getTabbedPaneBackground
  override def activeTextColor         : Color = UIUtil.getActiveTextColor
  override def inactiveTextColor       : Color = UIUtil.getInactiveTextColor
}

private object MockIJDefaultColorProvider extends ColorProvider {
  override def linkColor               : Color = Color.decode("#4b45a1")
  override def panelBgColor            : Color = Color.decode("#ececec")
  override def toolTipColor            : Color = Color.decode("#1d1d1d")
  override def buttonSelectColor       : Color = Color.decode("#ff6666")
  override def separatorFgColor        : Color = Color.decode("#cdcdcd")
  override def separatorBgColor        : Color = Color.decode("#FF0000")
  override def separatorShadowColor    : Color = Color.decode("#8e8e8e")
  override def separatorHighlightColor : Color = Color.decode("#ffffff")
  override def boundsColor             : Color = Color.decode("#c0c0c0")
  override def labelFgColor            : Color = Color.decode("#1d1d1d")
  override def tabbedPaneBgColor       : Color = Color.decode("#ececec")
  override def activeTextColor         : Color = Color.decode("#FF0000")
  override def inactiveTextColor       : Color = Color.decode("#999999")
}

private object MockDarkulaColorProvider extends ColorProvider {
  override def linkColor               : Color = Color.decode("#589df6")
  override def panelBgColor            : Color = Color.decode("#3c3f41")
  override def toolTipColor            : Color = Color.decode("#bbbbbb")
  override def buttonSelectColor       : Color = Color.decode("#ff6666")
  override def separatorFgColor        : Color = Color.decode("#515151")
  override def separatorBgColor        : Color = Color.decode("#FF0000")
  override def separatorShadowColor    : Color = Color.decode("#8e8e8e")
  override def separatorHighlightColor : Color = Color.decode("#ffffff")
  override def boundsColor             : Color = Color.decode("#323232")
  override def labelFgColor            : Color = Color.decode("#bbbbbb")
  override def tabbedPaneBgColor       : Color = Color.decode("#3c3f41")
  override def activeTextColor         : Color = Color.decode("#FF0000")
  override def inactiveTextColor       : Color = Color.decode("#999999")
}

