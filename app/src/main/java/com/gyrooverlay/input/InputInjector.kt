package com.gyrooverlay.input

import android.util.Log

class InputInjector {

    companion object {
        private const val TAG = "InputInjector"
        private const val EVENT_NODE = "/dev/input/event6"

        // Max raw coordinates from getevent -p
        private const val MAX_RAW_X = 17279
        private const val MAX_RAW_Y = 38399

        // Screen resolution (will be set from OverlayManager)
        var screenWidth = 1080
        var screenHeight = 2400
    }

    private var trackingId = 0
    private var rootProcess: Process? = null
    private var shellOutput: java.io.OutputStream? = null

    init {
        openRootShell()
    }

    private fun openRootShell() {
        try {
            rootProcess = Runtime.getRuntime().exec("su")
            shellOutput = rootProcess!!.outputStream
            Log.d(TAG, "Root shell opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open root shell: ${e.message}")
        }
    }

    // Convert screen pixel coords to raw input coords
    private fun toRawX(screenX: Float): Int {
        return ((screenX / screenWidth) * MAX_RAW_X).toInt().coerceIn(0, MAX_RAW_X)
    }

    private fun toRawY(screenY: Float): Int {
        return ((screenY / screenHeight) * MAX_RAW_Y).toInt().coerceIn(0, MAX_RAW_Y)
    }

    private fun sendEvent(type: Int, code: Int, value: Int) {
        val cmd = "sendevent $EVENT_NODE $type $code $value\n"
        try {
            shellOutput?.write(cmd.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "sendevent failed: ${e.message}")
            openRootShell() // Try to reopen shell
        }
    }

    private fun syn() = sendEvent(0, 0, 0)

    /**
     * Simulate a swipe gesture within the zone.
     * startX/Y and endX/Y are screen pixel coordinates.
     */
    fun injectSwipe(startX: Float, startY: Float, endX: Float, endY: Float) {
        val id = trackingId++ and 0xFFFF
        val rawSX = toRawX(startX)
        val rawSY = toRawY(startY)
        val rawEX = toRawX(endX)
        val rawEY = toRawY(endY)

        try {
            // Finger down
            sendEvent(3, 57, id)       // ABS_MT_TRACKING_ID
            sendEvent(3, 53, rawSX)    // ABS_MT_POSITION_X
            sendEvent(3, 54, rawSY)    // ABS_MT_POSITION_Y
            sendEvent(3, 48, 10)       // ABS_MT_TOUCH_MAJOR
            sendEvent(1, 330, 1)       // BTN_TOUCH down
            syn()

            // Move to end position (interpolate in steps for smoother feel)
            val steps = 3
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val ix = (rawSX + (rawEX - rawSX) * t).toInt()
                val iy = (rawSY + (rawEY - rawSY) * t).toInt()
                sendEvent(3, 57, id)
                sendEvent(3, 53, ix)
                sendEvent(3, 54, iy)
                syn()
            }

            // Finger up
            sendEvent(3, 57, -1)       // ABS_MT_TRACKING_ID = -1 (lift)
            sendEvent(1, 330, 0)       // BTN_TOUCH up
            syn()

            shellOutput?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "injectSwipe error: ${e.message}")
        }
    }

    fun close() {
        try {
            shellOutput?.close()
            rootProcess?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Close error: ${e.message}")
        }
    }
}
