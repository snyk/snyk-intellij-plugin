package snyk.amplitude.api

import com.google.gson.Gson
import org.hamcrest.core.IsNull.notNullValue
import org.junit.Assert.assertEquals
import org.junit.Test

class VariantTest {
    private val gson: Gson = Gson()

    @Test
    fun `parse variant with deprecated key field`() {
        val json = """{"key":"on"}"""

        val variant = gson.fromJson(json, Variant::class.java)

        assertEquals(notNullValue(), variant)
        assertEquals("on", variant.value)
    }

    @Test
    fun `parse variant with actual value field`() {
        val json = """{"value":"on"}"""

        val variant = gson.fromJson(json, Variant::class.java)

        assertEquals(notNullValue(), variant)
        assertEquals("on", variant.value)
    }
}
