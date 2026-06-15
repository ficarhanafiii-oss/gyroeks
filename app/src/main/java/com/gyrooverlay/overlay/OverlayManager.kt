package com.gyrooverlay.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import com.gyrooverlay.R
import com.gyrooverlay.databinding.OverlayControlBinding
import com.gyrooverlay.databinding.OverlayZoneBinding
import com.gyrooverlay.input.InputInjector
import com.gyrooverlay.sensor.GyroProcessor

class OverlayManager(
    private val context: Context,
    private val inputInjector: InputInjector,
    private val gyroProcessor: GyroProcessor
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // Views
    private var zoneView: View? = null
    private var controlView: View? = null
    private lateinit var zoneBinding: OverlayZoneBinding
    private lateinit var controlBinding: OverlayControlBinding

    // Zone position and size (screen pixels)
    private var zoneX = 100
    private var zoneY = 200
    private var zoneW = 400
    private var zoneH = 300

    // Screen size
    private var screenW = 1080
    private var screenH = 2400

    // Gyro loop
    private var gyroLoopRunning = false
    private val GYRO_INTERVAL_MS = 16L // ~60fps

    private fun getOverlayParams(x: Int, y: Int, w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    fun show() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels

        InputInjector.screenWidth = screenW
        InputInjector.screenHeight = screenH

        // Default zone: top-right area (common camera area in mobile games)
        zoneX = screenW / 2
        zoneY = screenH / 6
        zoneW = screenW / 2 - 20
        zoneH = screenH / 3

        showZone()
        showControl()
        startGyroLoop()
    }

    private fun showZone() {
        val inflater = LayoutInflater.from(context)
        zoneBinding = OverlayZoneBinding.inflate(inflater)
        val view = zoneBinding.root

        val params = getOverlayParams(zoneX, zoneY, zoneW, zoneH)
        windowManager.addView(view, params)
        zoneView = view

        setupZoneDragResize(view, params)
    }

    private fun showControl() {
        val inflater = LayoutInflater.from(context)
        controlBinding = OverlayControlBinding.inflate(inflater)
        val view = controlBinding.root

        val params = getOverlayParams(10, screenH / 2, 200, 320)
        windowManager.addView(view, params)
        controlView = view

        setupControlUI(view, params)
    }

    private fun setupControlUI(view: View, params: WindowManager.LayoutParams) {
        // Toggle gyro ON/OFF
        controlBinding.btnToggle.setOnClickListener {
            gyroProcessor.enabled = !gyroProcessor.enabled
            controlBinding.btnToggle.text = if (gyroProcessor.enabled) "ON" else "OFF"
            gyroProcessor.reset()
        }

        // Sensitivity seekbar
        controlBinding.seekSensitivity.progress = 8
        controlBinding.seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val sens = progress.toFloat().coerceAtLeast(1f)
                gyroProcessor.sensitivityX = sens
                gyroProcessor.sensitivityY = sens
                controlBinding.tvSens.text = "Sens: $progress"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Drag control panel
        var cx = 10; var cy = screenH / 2
        var lastTX = 0f; var lastTY = 0f
        controlBinding.tvDrag.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTX = event.rawX; lastTY = event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    cx += (event.rawX - lastTX).toInt()
                    cy += (event.rawY - lastTY).toInt()
                    lastTX = event.rawX; lastTY = event.rawY
                    params.x = cx; params.y = cy
                    windowManager.updateViewLayout(view, params)
                }
            }
            true
        }
    }

    private fun setupZoneDragResize(view: View, params: WindowManager.LayoutParams) {
        var lastTX = 0f; var lastTY = 0f
        var mode = "drag" // "drag" or "resize"

        // Drag handle (top area of zone)
        zoneBinding.zoneDragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTX = event.rawX; lastTY = event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    zoneX += (event.rawX - lastTX).toInt()
                    zoneY += (event.rawY - lastTY).toInt()
                    lastTX = event.rawX; lastTY = event.rawY
                    params.x = zoneX; params.y = zoneY
                    windowManager.updateViewLayout(view, params)
                }
            }
            true
        }

        // Resize handle (bottom-right corner)
        zoneBinding.zoneResizeHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastTX = event.rawX; lastTY = event.rawY }
                MotionEvent.ACTION_MOVE -> {
                    zoneW = (zoneW + (event.rawX - lastTX).toInt()).coerceAtLeast(100)
                    zoneH = (zoneH + (event.rawY - lastTY).toInt()).coerceAtLeast(100)
                    lastTX = event.rawX; lastTY = event.rawY
                    params.width = zoneW; params.height = zoneH
                    windowManager.updateViewLayout(view, params)
                }
            }
            true
        }
    }

    private fun startGyroLoop() {
        gyroLoopRunning = true
        handler.post(object : Runnable {
            override fun run() {
                if (!gyroLoopRunning) return

                if (gyroProcessor.enabled) {
                    val (dx, dy) = gyroProcessor.consumeDelta()

                    // Only inject if movement is significant
                    if (Math.abs(dx) > 0.5f || Math.abs(dy) > 0.5f) {
                        // Center of zone as start point
                        val startX = zoneX + zoneW / 2f
                        val startY = zoneY + zoneH / 2f
                        val endX = startX + dx
                        val endY = startY + dy

                        // Clamp end to zone bounds
                        val clampedEndX = endX.coerceIn(zoneX.toFloat(), (zoneX + zoneW).toFloat())
                        val clampedEndY = endY.coerceIn(zoneY.toFloat(), (zoneY + zoneH).toFloat())

                        Thread {
                            inputInjector.injectSwipe(startX, startY, clampedEndX, clampedEndY)
                        }.start()
                    }
                }

                handler.postDelayed(this, GYRO_INTERVAL_MS)
            }
        })
    }

    fun hide() {
        gyroLoopRunning = false
        handler.removeCallbacksAndMessages(null)
        try {
            zoneView?.let { windowManager.removeView(it) }
            controlView?.let { windowManager.removeView(it) }
        } catch (e: Exception) { /* ignore */ }
        inputInjector.close()
    }
}
