package com.example.hotspotloginapp

import android.content.Context
import android.util.Log // Ensure Log is imported
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class AppNanoHttpd(
    private val context: Context,
    port: Int,
    private val authCallback: AuthCallback? // Kept nullable as per previous version
) : NanoHTTPD(port) {

    private val TAG = "AppNanoHttpd" // Added TAG for logging consistency

    interface AuthCallback {
        fun onAuthSuccess()
    }

    // Helper to read assets
    private fun readAsset(filename: String): String? {
        return try {
            Log.d(TAG, "Reading asset: $filename")
            context.assets.open(filename).bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading asset $filename", e)
            null
        }
    }

    override fun serve(session: IHTTPSession): Response {
        Log.d(TAG, "Serve request: Uri = ${session.uri}, Method = ${session.method}")

        // Handle GET requests for mock service selection (redirects)
        if (session.method == Method.GET) {
            when (session.uri) {
                "/select_mock_gmail" -> {
                    Log.d(TAG, "Redirecting to /mock_gmail_login.html")
                    return newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "").apply { addHeader("Location", "/mock_gmail_login.html") }
                }
                "/select_mock_outlook" -> {
                    Log.d(TAG, "Redirecting to /mock_outlook_login.html")
                    return newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "").apply { addHeader("Location", "/mock_outlook_login.html") }
                }
                "/select_mock_icloud" -> {
                    Log.d(TAG, "Redirecting to /mock_icloud_login.html")
                    return newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "").apply { addHeader("Location", "/mock_icloud_login.html") }
                }
            }
        }

        // Handle GET requests for serving HTML pages (main login and mock logins)
        if (session.method == Method.GET) {
            val pageToServe = when (session.uri) {
                "/", "/login.html", "/index.html" -> "login.html" // Added /index.html as common alias for /
                "/mock_gmail_login.html" -> "mock_gmail_login.html"
                "/mock_outlook_login.html" -> "mock_outlook_login.html"
                "/mock_icloud_login.html" -> "mock_icloud_login.html"
                else -> null
            }
            if (pageToServe != null) {
                val htmlContent = readAsset(pageToServe)
                return if (htmlContent != null) {
                    Log.d(TAG, "Serving page: $pageToServe")
                    newFixedLengthResponse(Response.Status.OK, MIME_HTML, htmlContent)
                } else {
                    Log.e(TAG, "Failed to load page: $pageToServe from assets")
                    newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Error: Could not load page.")
                }
            }
        }

        // Handle POST requests (simple password login and mock logins)
        if (session.method == Method.POST) {
            // val params = mutableMapOf<String, String>() // Not needed if directly using session.parms
            val files = mutableMapOf<String, String>() // Needed for parseBody for file uploads, but also for general body parsing
            try {
                session.parseBody(files) // This populates session.parms for x-www-form-urlencoded
            } catch (e: IOException) {
                Log.e(TAG, "IOException parsing POST body", e)
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + e.message)
            } catch (e: ResponseException) {
                Log.e(TAG, "ResponseException parsing POST body", e)
                return newFixedLengthResponse(e.status, MIME_PLAINTEXT, e.message)
            }

            when (session.uri) {
                "/login" -> { // Original simple password login
                    val submittedPassword = session.parms["password"]
                    Log.d(TAG, "Password login attempt. Submitted: '$submittedPassword'")

                    if (submittedPassword == "password123") { // Hardcoded password
                        Log.i(TAG, "Simple password login successful.")
                        authCallback?.onAuthSuccess()
                        return newFixedLengthResponse(Response.Status.OK, MIME_HTML, "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>body{font-family:sans-serif;text-align:center;padding-top:50px;}</style></head><body><h2>Login Successful!</h2><p>You can now access the internet.</p></body></html>")
                    } else {
                        Log.w(TAG, "Simple password login failed.")
                        return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_HTML, "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>body{font-family:sans-serif;text-align:center;padding-top:50px;}</style></head><body><h2>Login Failed!</h2><p><a href='/'>Try again</a></p></body></html>")
                    }
                }
                "/login/mockgmail" -> {
                    Log.i(TAG, "MockGmail login attempt processed as success.")
                    authCallback?.onAuthSuccess()
                    return newFixedLengthResponse(Response.Status.OK, MIME_HTML, "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>body{font-family:sans-serif;text-align:center;padding-top:50px;}</style></head><body><h2>MockGmail Login Successful!</h2><p>You can now access the internet.</p></body></html>")
                }
                "/login/mockoutlook" -> {
                    Log.i(TAG, "MockOutlook login attempt processed as success.")
                    authCallback?.onAuthSuccess()
                    return newFixedLengthResponse(Response.Status.OK, MIME_HTML, "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>body{font-family:sans-serif;text-align:center;padding-top:50px;}</style></head><body><h2>MockOutlook Login Successful!</h2><p>You can now access the internet.</p></body></html>")
                }
                "/login/mockicloud" -> {
                    Log.i(TAG, "MockiCloud login attempt processed as success.")
                    authCallback?.onAuthSuccess()
                    return newFixedLengthResponse(Response.Status.OK, MIME_HTML, "<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>body{font-family:sans-serif;text-align:center;padding-top:50px;}</style></head><body><h2>MockiCloud Login Successful!</h2><p>You can now access the internet.</p></body></html>")
                }
            }
        }

        // Default response if no routes matched
        Log.d(TAG, "No matching route for Uri: ${session.uri}, Method: ${session.method}")
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }
}
