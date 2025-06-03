package de.lulebe.designer.online

import android.content.Context
import com.beust.klaxon.JsonObject
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody


class AccountManager (context: Context) {

    val JSON = MediaType.parse("application/json")

    val mSP = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    val mOkClient = OkHttpClient()

    init {

    }

    val isSignedIn: Boolean
        get() = mSP.getString("username", null) != null

    fun signOut() {
        mSP.edit().let {
            it.clear()
            it.commit()
        }
    }

    fun signIn(username: String, password: String) {
        val body = RequestBody.create(JSON, JsonObject(
                mapOf(
                        Pair("username", username),
                        Pair("password", password)
                )
        ).toJsonString())
        val request = Request.Builder()
                .url("https://designer.lulebe.net/users/login")
                .post(body)
                .build()
        val response = mOkClient.newCall(request).execute()
    }

    fun signUp(username: String, password: String) {
        val body = RequestBody.create(JSON, JsonObject(
                mapOf(
                        Pair("username", username),
                        Pair("password", password)
                )
        ).toJsonString())
        val request = Request.Builder()
                .url("https://designer.lulebe.net/users")
                .post(body)
                .build()
        val response = mOkClient.newCall(request).execute()
    }

}