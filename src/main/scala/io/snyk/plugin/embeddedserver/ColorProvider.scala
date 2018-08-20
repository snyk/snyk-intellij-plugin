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
  def mockIntellijDefault: ColorProvider = MockIJDefaultColorProvider
  def mockIntellijDarkula: ColorProvider = MockDarkulaColorProvider
}

import ColorProvider._


trait ColorProvider {
  def linkColor: Color
  def bgColor: Color
  def textColor: Color
  def inactiveTextColor: Color
  def separatorColor: Color

  //unused, delete?
  def boundsColor: Color
  def toolTipColor: Color
  def buttonSelectColor: Color
  def separatorShadowColor: Color
  def separatorHighlightColor: Color


  //TODO: decent macro solution is needed - this is WAY too fragile
  def toMap: Map[String, Color] = Map(
    "linkColor"               -> linkColor,
    "bgColor"                 -> bgColor,
    "textColor"               -> textColor,
    "inactiveTextColor"       -> inactiveTextColor,
    "separatorColor"          -> separatorColor,
    "boundsColor"             -> boundsColor,
    "toolTipColor"            -> toolTipColor,
    "buttonSelectColor"       -> buttonSelectColor,
    "separatorShadowColor"    -> separatorShadowColor,
    "separatorHighlightColor" -> separatorHighlightColor,
  )
}

private object IntelliJColorProvider extends ColorProvider {
  def bgBrightness = UIUtil.getPanelBackground.brightness
  override def linkColor = if(bgBrightness > 0.5f) new Color(75, 69, 161) else new Color(88, 157, 246)
  override def bgColor                 : Color = UIUtil.getPanelBackground
  override def textColor               : Color = UIUtil.getLabelForeground
  override def inactiveTextColor       : Color = UIUtil.getInactiveTextColor
  override def separatorColor          : Color = UIUtil.getSeparatorForeground

  //unused, delete?
  override def boundsColor             : Color = UIUtil.getBoundsColor
  override def toolTipColor            : Color = UIUtil.getToolTipForeground
  override def buttonSelectColor       : Color = UIUtil.getButtonSelectColor
  override def separatorShadowColor    : Color = UIUtil.getSeparatorShadow
  override def separatorHighlightColor : Color = UIUtil.getSeparatorHighlight
}

private object MockIJDefaultColorProvider extends ColorProvider {
  override def linkColor               : Color = Color.decode("#4b45a1")
  override def bgColor                 : Color = Color.decode("#ececec")
  override def textColor               : Color = Color.decode("#1d1d1d")
  override def inactiveTextColor       : Color = Color.decode("#999999")
  override def separatorColor          : Color = Color.decode("#cdcdcd")

  //unused, delete?
  override def boundsColor             : Color = Color.decode("#c0c0c0")
  override def toolTipColor            : Color = Color.decode("#1d1d1d")
  override def buttonSelectColor       : Color = Color.decode("#ff6666")
  override def separatorShadowColor    : Color = Color.decode("#8e8e8e")
  override def separatorHighlightColor : Color = Color.decode("#ffffff")
}

private object MockDarkulaColorProvider extends ColorProvider {
  override def linkColor               : Color = Color.decode("#589df6")
  override def bgColor                 : Color = Color.decode("#3c3f41")
  override def textColor               : Color = Color.decode("#bbbbbb")
  override def inactiveTextColor       : Color = Color.decode("#999999")
  override def separatorColor          : Color = Color.decode("#515151")

  //unused, delete?
  override def boundsColor             : Color = Color.decode("#323232")
  override def toolTipColor            : Color = Color.decode("#bbbbbb")
  override def buttonSelectColor       : Color = Color.decode("#ff6666")
  override def separatorShadowColor    : Color = Color.decode("#8e8e8e")
  override def separatorHighlightColor : Color = Color.decode("#ffffff")
}

