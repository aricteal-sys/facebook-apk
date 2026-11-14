package com.fb.login

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var progress = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val progressBar = findViewById<ProgressBar>(R.id.splashProgress)
        val percentText = findViewById<TextView>(R.id.splashPercent)
        val subtitle = findViewById<TextView>(R.id.splashSubtitle)

        val messages = listOf(
            Pair(0, "Checking for updates…"),
            Pair(25, "Downloading latest version…"),
            Pair(50, "Installing update…"),
            Pair(75, "Almost ready…"),
            Pair(95, "Launching Facebook…")
        )

        val runnable = object : Runnable {
            override fun run() {
                progress += 2
                progressBar.progress = progress
                percentText.text = "$progress%"

                for ((threshold, msg) in messages) {
                    if (progress == threshold) {
                        subtitle.text = msg
                    }
                }

                if (progress >= 100) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                } else {
                    handler.postDelayed(this, 80)
                }
            }
        }

        handler.post(runnable)
    }
}
