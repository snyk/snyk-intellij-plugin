package io.snyk.plugin.net

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * An endpoint to get information about users and groups.
 */
interface ReportFalsePositiveRetrofitService {
    @POST(apiName)
    fun report(@Body payload: FalsePositivePayload): Call<Unit>

    companion object {
        const val apiName = "feedback/sast"
    }
}

data class FalsePositivePayload(
    val topic: String,
    val message: String,
    val feedbackOrigin: String = "ide",
    val context: FalsePositiveContext
)

data class FalsePositiveContext(
    val issueId: String,
    val userPublicId: String,
    val startLine: Int,
    val endLine: Int,
    val primaryFilePath: String,
    val vulnName: String,
    val fileContents: String
)
