package io.snyk.plugin.ui.state

import enumeratum.{Enum, EnumEntry}

sealed trait Flag extends EnumEntry

object Flag extends Enum[Flag] {
  val values = findValues
  case object HideMavenGroups extends Flag
}
