package snyk.amplitude.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface VariantService {
    @POST("sdk/vardata")
    fun sdkVardata(@Body user: ExperimentUser): Call<Map<String, Variant>>
}

data class Variant(
    @SerializedName("value", alternate = ["key"])
    val value: String? = null,

    @SerializedName("payload")
    val payload: Any? = null
)
