package com.parcha.spoilnot

import com.google.firebase.FirebaseApp
import com.parcha.spoilnot.core.firebase.FirebaseEmulators
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the server holds, read past the security rules through the emulators' admin access (`Bearer owner`), for
 * checks no signed-in user could make, such as after an account is deleted. Blocking: call off the main thread.
 */
object EmulatorAdmin {
    private val host get() = BuildConfig.FIREBASE_EMULATOR_HOST
    private val project get() = checkNotNull(FirebaseApp.getInstance().options.projectId)
    private val documents
        get() = "http://$host:${FirebaseEmulators.FIRESTORE_PORT}/v1/projects/$project/databases/(default)/documents"

    fun documentExists(path: String): Boolean = when (val code = request("$documents/$path").first) {
        HttpURLConnection.HTTP_OK -> true
        HttpURLConnection.HTTP_NOT_FOUND -> false
        else -> error("GET $path: HTTP $code")
    }

    /** IDs of the documents in [collection] (the documents themselves, whether or not their parent exists). */
    fun documentIds(collection: String): List<String> {
        val (code, body) = request("$documents/$collection")
        check(code == HttpURLConnection.HTTP_OK) { "GET $collection: HTTP $code" }
        val docs = JSONObject(body).optJSONArray("documents") ?: JSONArray()
        return (0 until docs.length()).map { docs.getJSONObject(it).getString("name").substringAfterLast('/') }
    }

    /** Deletes a document as the reminder job's prune would. */
    fun deleteDocument(path: String) {
        val (code, _) = request("$documents/$path", method = "DELETE")
        check(code == HttpURLConnection.HTTP_OK) { "DELETE $path: HTTP $code" }
    }

    fun authUserExists(uid: String): Boolean {
        val url = "http://$host:${FirebaseEmulators.AUTH_PORT}/identitytoolkit.googleapis.com/v1/projects/$project" +
            "/accounts:lookup"
        val (code, body) = request(url, JSONObject().put("localId", JSONArray().put(uid)).toString())
        check(code == HttpURLConnection.HTTP_OK) { "accounts:lookup: HTTP $code" }
        return (JSONObject(body).optJSONArray("users")?.length() ?: 0) > 0
    }

    private fun request(url: String, postBody: String? = null, method: String? = null): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.setRequestProperty("Authorization", "Bearer owner")
            if (method != null) connection.requestMethod = method
            if (postBody != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(postBody.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }
}
