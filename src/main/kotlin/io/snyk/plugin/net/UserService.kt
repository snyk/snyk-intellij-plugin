package io.snyk.plugin.net

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.GET

/**
 * An endpoint to get information about users and groups.
 */
interface UserService {
    @GET(apiName)
    fun userMe(): Call<User>

    companion object {
        const val apiName = "user/me"
    }
}

data class User(
    @SerializedName("id")
    val id: String
)
