package com.example.hotspotloginapp

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class AppNanoHttpd(
    private val context: Context,
    port: Int,
    private val authCallback: AuthCallback? // Made nullable for flexibility if not always needed
) : NanoHTTPD(port) {

    private val TAG = "AppNanoHttpd"

    interface AuthCallback {
        fun onAuthSuccess()
    }

    override fun serve(session: IHTTPSession): Response {
        Log.d(TAG, "Serve request: Uri = ${session.uri}, Method = ${session.method}")
        return when {
            session.method == Method.GET && (session.uri == "/" || session.uri == "/login.html" || session.uri.startsWith("/index.html")) -> {
                try {
                    val htmlContent = context.assets.open("login.html").bufferedReader().use { it.readText() }
                    newFixedLengthResponse(Response.Status.OK, "text/html", htmlContent)
                } catch (e: IOException) {
                    Log.e(TAG, "Error reading login.html from assets", e)
                    newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error serving login page.")
                }
            }
            session.method == Method.POST && session.uri == "/login" -> {
                val params = mutableMapOf<String, String>()
                try {
                    session.parseBody(params)
                } catch (e: IOException) {
                    Log.e(TAG, "IOException parsing POST body", e)
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error processing request.")
                } catch (e: ResponseException) {
                    Log.e(TAG, "ResponseException parsing POST body", e)
                    return newFixedLengthResponse(e.status, MIME_PLAINTEXT, e.message)
                }

                val submittedPassword = params["password"]
                Log.d(TAG, "Submitted password: '$submittedPassword'")
                val hardcodedPassword = "password123"

                if (submittedPassword == hardcodedPassword) {
                    Log.i(TAG, "Login successful for password: $submittedPassword")
                    authCallback?.onAuthSuccess() // Call the callback
                    newFixedLengthResponse(Response.Status.OK, "text/html", "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>body{font-family:sans-serif;text-align:center;padding-top:50px;}</style></head><body><h2>Login Successful!</h2><p>You can now access the internet.</p></body></html>")
                } else {
                    Log.w(TAG, "Login failed. Submitted: '$submittedPassword'")
                    newFixedLengthResponse(Response.Status.UNAUTHORIZED, "text/html", "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>body{font-family:sans-serif;text-align:center;padding-top:50px;}</style></head><body><h2>Login Failed!</h2><p><a href='/'>Try again</a></p></body></html>")
                }
            }
            else -> {
                Log.d(TAG, "Not found: ${session.uri}")
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        }
    }
}
