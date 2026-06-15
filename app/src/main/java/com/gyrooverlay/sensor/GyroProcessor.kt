package com.gyrooverlay.sensor

class GyroProcessor {

    // High-pass filter alpha (0.0 = block all, 1.0 = pass all)
    private val FILTER_ALPHA = 0.8f
    private val DEAD_ZONE = 0.02f // rad/s threshold

    // Sensitivity multiplier (configurable)
    var sensitivityX = 8f
    var sensitivityY = 8f

    // Gyro enabled flag
    var enabled = true

    // Internal state
    private var filteredX = 0f
    private var filteredY = 0f
    private var lastTimestamp = 0L

    // Accumulated delta (consumed by input injector)
    private var deltaX = 0f
    private var deltaY = 0f

    // Lock for thread safety
    private val lock = Any()

    fun onGyroData(rawX: Float, rawY: Float, rawZ: Float) {
        if (!enabled) return

        // High-pass filter to remove drift
        filteredX = FILTER_ALPHA * filteredX + (1 - FILTER_ALPHA) * rawX
        filteredY = FILTER_ALPHA * filteredY + (1 - FILTER_ALPHA) * rawY

        val highPassX = rawX - filteredX
        val highPassY = rawY - filteredY

        // Dead zone — ignore tiny movements
        val ax = if (Math.abs(highPassX) > DEAD_ZONE) highPassX else 0f
        // Note: Y-axis of gyro maps to horizontal camera movement (yaw)
        // X-axis of gyro maps to vertical camera movement (pitch)
        val ay = if (Math.abs(highPassY) > DEAD_ZONE) highPassY else 0f

        synchronized(lock) {
            // Gyro Y (yaw) → horizontal swipe
            // Gyro X (pitch) → vertical swipe
            // Negate because tilting right should move camera right
            deltaX += -ay * sensitivityX
            deltaY += ax * sensitivityY
        }
    }

    fun consumeDelta(): Pair<Float, Float> {
        synchronized(lock) {
            val dx = deltaX
            val dy = deltaY
            deltaX = 0f
            deltaY = 0f
            return Pair(dx, dy)
        }
    }

    fun reset() {
        synchronized(lock) {
            deltaX = 0f
            deltaY = 0f
            filteredX = 0f
            filteredY = 0f
        }
    }
}
