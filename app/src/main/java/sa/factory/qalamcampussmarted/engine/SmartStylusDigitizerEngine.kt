package sa.factory.qalamcampussmarted.engine

import kotlin.math.atan2
import kotlin.math.sqrt

enum class StylusButton {
    NONE,
    ERASER,
    BARREL_CLICK,
    COLOR_SWAP
}

data class StylusPacket(
    val rawPressure: Int,
    val tiltXDeg: Float,
    val tiltYDeg: Float,
    val buttonMask: Int,
    val contactRadiusMm: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class ProcessedStrokePoint(
    val rawPressure: Int,
    val normalizedPressure: Float,
    val tiltAzimuthDeg: Float,
    val tiltAltitudeDeg: Float,
    val isPalmRejected: Boolean,
    val buttonPressed: StylusButton,
    val velocityPixelsPerSec: Float,
    val smoothedPressure: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class StylusStrokeStats(
    val totalPoints: Int,
    val averagePressure: Float,
    val peakPressure: Float,
    val palmRejectionsCount: Int,
    val averageVelocityPxPerSec: Float,
    val activeDurationMs: Long
)

class SmartStylusDigitizerEngine(
    private val smoothingAlpha: Float = 0.35f,
    private val palmRadiusThresholdMm: Float = 7.5f,
    private val minActivePressure: Int = 32
) {
    companion object {
        const val MAX_PRESSURE_LEVELS = 4096
    }

    private var previousSmoothedPressure: Float = 0.0f
    private var lastTimestamp: Long = 0L

    fun processStylusPacket(
        packet: StylusPacket,
        prevPoint: ProcessedStrokePoint? = null,
        dx: Float = 0f,
        dy: Float = 0f
    ): ProcessedStrokePoint {
        val clampedPressure = packet.rawPressure.coerceIn(0, MAX_PRESSURE_LEVELS)
        val normalized = clampedPressure.toFloat() / MAX_PRESSURE_LEVELS.toFloat()

        // Palm Rejection heuristic: large contact surface or sub-threshold ghost touch
        val isPalm = (packet.contactRadiusMm >= palmRadiusThresholdMm && clampedPressure < 350) ||
                (clampedPressure < minActivePressure)

        // Tilt Azimuth and Altitude
        val radToDeg = (180.0 / Math.PI).toFloat()
        val azimuth = (atan2(packet.tiltYDeg.toDouble(), packet.tiltXDeg.toDouble()) * radToDeg).toFloat()
        val compositeTilt = sqrt((packet.tiltXDeg * packet.tiltXDeg + packet.tiltYDeg * packet.tiltYDeg).toDouble()).toFloat()
        val altitude = (90f - compositeTilt).coerceIn(0f, 90f)

        // Button decoding
        val button = when (packet.buttonMask) {
            1 -> StylusButton.ERASER
            2 -> StylusButton.BARREL_CLICK
            4 -> StylusButton.COLOR_SWAP
            else -> StylusButton.NONE
        }

        // EMA Pressure Smoothing
        val smoothed = if (prevPoint == null) {
            normalized
        } else {
            smoothingAlpha * normalized + (1f - smoothingAlpha) * prevPoint.smoothedPressure
        }
        previousSmoothedPressure = smoothed

        // Dynamic stroke velocity
        val dt = if (lastTimestamp > 0L && packet.timestamp > lastTimestamp) {
            (packet.timestamp - lastTimestamp).toFloat() / 1000f
        } else {
            0.016f // Default 60Hz frame delta
        }
        lastTimestamp = packet.timestamp

        val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        val velocity = if (dt > 0.001f) distance / dt else 0f

        return ProcessedStrokePoint(
            rawPressure = clampedPressure,
            normalizedPressure = normalized,
            tiltAzimuthDeg = azimuth,
            tiltAltitudeDeg = altitude,
            isPalmRejected = isPalm,
            buttonPressed = button,
            velocityPixelsPerSec = velocity,
            smoothedPressure = smoothed,
            timestamp = packet.timestamp
        )
    }

    fun createSyntheticPacket(
        pressure: Int,
        tiltX: Float = 15f,
        tiltY: Float = 10f,
        contactRadiusMm: Float = 2.0f,
        buttonMask: Int = 0,
        timestamp: Long = System.currentTimeMillis()
    ): StylusPacket {
        return StylusPacket(
            rawPressure = pressure,
            tiltXDeg = tiltX,
            tiltYDeg = tiltY,
            buttonMask = buttonMask,
            contactRadiusMm = contactRadiusMm,
            timestamp = timestamp
        )
    }

    fun calculateStrokeStats(points: List<ProcessedStrokePoint>): StylusStrokeStats {
        if (points.isEmpty()) {
            return StylusStrokeStats(0, 0f, 0f, 0, 0f, 0L)
        }

        val validPoints = points.filter { !it.isPalmRejected }
        val palmCount = points.count { it.isPalmRejected }
        val avgPressure = if (validPoints.isNotEmpty()) validPoints.map { it.normalizedPressure }.average().toFloat() else 0f
        val peakPressure = if (validPoints.isNotEmpty()) validPoints.maxOf { it.normalizedPressure } else 0f
        val avgVelocity = if (validPoints.isNotEmpty()) validPoints.map { it.velocityPixelsPerSec }.average().toFloat() else 0f
        val duration = if (points.size > 1) points.last().timestamp - points.first().timestamp else 0L

        return StylusStrokeStats(
            totalPoints = points.size,
            averagePressure = avgPressure,
            peakPressure = peakPressure,
            palmRejectionsCount = palmCount,
            averageVelocityPxPerSec = avgVelocity,
            activeDurationMs = duration
        )
    }
}
