package snyk.amplitude.api

import com.google.gson.Gson
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.notNullValue
import org.junit.Assert.assertThat
import org.junit.Test

class VariantTest {
    private val gson: Gson = Gson()

    @Test
    fun `parse variant with deprecated key field`() {
        val json = """{"key":"on"}"""

        val variant = gson.fromJson(json, Variant::class.java)

        assertThat(variant, notNullValue())
        assertThat(variant.value, equalTo("on"))
    }

    @Test
    fun `parse variant with actual value field`() {
        val json = """{"value":"on"}"""

        val variant = gson.fromJson(json, Variant::class.java)

        assertThat(variant, notNullValue())
        assertThat(variant.value, equalTo("on"))
    }
}
