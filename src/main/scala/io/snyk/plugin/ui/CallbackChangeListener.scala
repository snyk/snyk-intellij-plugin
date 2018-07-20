package io.snyk.plugin.ui

import javafx.beans.value.{ChangeListener, ObservableValue}

object CallbackChangeListener {
  class Followup {
    var removeListener: Boolean = false
  }
  def apply[T](cb: (T, T, Followup) => Unit): ChangeListener[T] = new CallbackChangeListener(cb)
}

import CallbackChangeListener._

class CallbackChangeListener[T](cb: (T, T, Followup) => Unit) extends ChangeListener[T] {
  override def changed(
    observable: ObservableValue[_ <: T],
    oldValue: T,
    newValue: T
  ): Unit = {
    val followup = new Followup
    cb(oldValue, newValue, followup)
    if(followup.removeListener) {
      observable.removeListener(this)
    }
  }
}
