package io.snyk.plugin.ui.state

import monix.execution.atomic.Atomic

class Flags {
  val flagsMap: Atomic[Map[Flag, Boolean]] = Atomic(Map.empty[Flag, Boolean])

  def update(flag: Flag, newVal: Boolean): Unit =
    flagsMap transform {
      _.updated(flag, newVal)
    }

  def apply(flag: Flag): Boolean = flagsMap.get.getOrElse(flag, false)

  def toggle(flag: Flag): Boolean = {
    flagsMap transform { map =>
      val oldVal = map.getOrElse(flag, false)
      map.updated(flag, !oldVal)
    }
    flagsMap.get(flag)
  }

  def asStringMap: Map[String, Boolean] = flagsMap.get map { case (k, v) => k.entryName.toLowerCase -> v }
}
