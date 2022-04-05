import java.awt.Component
import java.awt.Container
import kotlin.reflect.KClass
import kotlin.reflect.cast

object UIComponentFinder {

    @OptIn(ExperimentalStdlibApi::class)
    fun <T: Component> getComponentByName(parent: Container, clazz: KClass<T>, name: String? = null): T? {
        val components = parent.components
        var found: T? = null
        for (component in components) {
            if (clazz.isInstance(component) && (name == null || name == component.name)) {
                found = clazz.cast(component)
            } else if (component is Container) {
                found = getComponentByName(component, clazz, name)
            }
            if (found != null) {
                break
            }
        }
        return found
    }
}
