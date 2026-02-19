package snyk.common

import java.awt.Component
import java.awt.Container
import kotlin.reflect.KClass
import kotlin.reflect.cast

object UIComponentFinder {

  fun <T : Component> getComponentByName(
    parent: Container,
    clazz: KClass<T>,
    name: String? = null,
  ): T? = getComponentByCondition(parent, clazz) { name == null || name == it.name }

  @Suppress("UNCHECKED_CAST")
  fun <T : Component> getComponentByCondition(
    parent: Container,
    clazz: KClass<T>,
    condition: (T) -> Boolean,
  ): T? {
    val components = parent.components
    var found: T? = null
    for (component in components) {
      if (clazz.isInstance(component) && condition(component as T)) {
        found = clazz.cast(component)
      } else if (component is Container) {
        found = getComponentByCondition(component, clazz, condition)
      }
      if (found != null) {
        break
      }
    }
    return found
  }
}
