package com.gyrooverlay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gyrooverlay.databinding.ActivityMainBinding
import com.gyrooverlay.service.GyroService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val OVERLAY_PERMISSION_REQ = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        updateStatus()

        binding.btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                requestOverlayPermission()
                return@setOnClickListener
            }
            startGyroService()
        }

        binding.btnStop.setOnClickListener {
            stopGyroService()
        }

        binding.btnCheckRoot.setOnClickListener {
            checkRoot()
        }
    }

    private fun checkRoot() {
        Thread {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "echo root_ok"))
                val result = process.inputStream.bufferedReader().readLine()
                runOnUiThread {
                    if (result == "root_ok") {
                        binding.tvRootStatus.text = "✅ Root OK"
                        Toast.makeText(this, "Root access granted!", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.tvRootStatus.text = "❌ Root Failed"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvRootStatus.text = "❌ Root Error: ${e.message}"
                }
            }
        }.start()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, OVERLAY_PERMISSION_REQ)
    }

    private fun startGyroService() {
        val intent = Intent(this, GyroService::class.java)
        startForegroundService(intent)
        binding.tvStatus.text = "Status: Running ✅"
        Toast.makeText(this, "Gyro Overlay started! Look for floating button.", Toast.LENGTH_LONG).show()
    }

    private fun stopGyroService() {
        val intent = Intent(this, GyroService::class.java)
        stopService(intent)
        binding.tvStatus.text = "Status: Stopped ⛔"
    }

    private fun updateStatus() {
        val running = GyroService.isRunning
        binding.tvStatus.text = if (running) "Status: Running ✅" else "Status: Stopped ⛔"
        binding.tvOverlayStatus.text = if (Settings.canDrawOverlays(this))
            "Overlay Permission: ✅" else "Overlay Permission: ❌ (tap Start)"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ) {
            updateStatus()
            if (Settings.canDrawOverlays(this)) {
                startGyroService()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
