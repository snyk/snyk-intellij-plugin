package io.snyk.plugin.ui.settings

trait SnykIntelliJSettings {
  def getCustomEndpointUrl(): String
  def getOrganization(): String
  def isIgnoreUnknownCA(): Boolean
}

object SnykIntelliJSettings {
  val Empty = new SnykIntelliJSettings {
    override def getCustomEndpointUrl(): String = ""

    override def getOrganization(): String = ""

    override def isIgnoreUnknownCA(): Boolean = false
  }
}
