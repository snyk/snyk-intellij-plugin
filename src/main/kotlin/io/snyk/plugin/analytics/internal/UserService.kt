package io.snyk.plugin.analytics.internal

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET

/**
 * An endpoint to get information about users and groups.
 */
interface UserService {
    @GET("user/me")
    fun userMe(): Call<User>
}

data class User(
    @SerializedName("id")
    val id: String
)
