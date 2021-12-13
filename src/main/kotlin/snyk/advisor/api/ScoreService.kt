package snyk.advisor.api

import com.google.gson.annotations.SerializedName
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import kotlin.math.roundToInt

/**
 * An endpoint to get information about the scoring system for packages.
 */
interface ScoreService {
    @POST("scores/npm-package")
    fun scoresNpmPackages(@Body packages: List<String>): Call<List<PackageInfo>>

    @POST("scores/python")
    fun scoresPythonPackages(@Body packages: List<String>): Call<List<PackageInfo>>
}

data class PackageInfo(
    @SerializedName("name")
    val name: String,

    @SerializedName("score")
    val score: Double,

    @SerializedName("pending")
    val pending: Boolean,

    @SerializedName("labels")
    val labels: PackageInfoLabels?,

    @SerializedName("error")
    val error: String?
){
    val normalizedScore: Int
        get() = (score * 100).roundToInt()
}

data class PackageInfoLabels(
    @SerializedName("popularity")
    val popularity: String,

    @SerializedName("maintenance")
    val maintenance: String,

    @SerializedName("community")
    val community: String,

    @SerializedName("security")
    val security: String
)
