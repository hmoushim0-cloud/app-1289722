package sa.factory.qalamcampussmarted.engine

import java.security.MessageDigest
import kotlin.math.pow

enum class AttendanceStatus {
    DISCONNECTED,
    PROXIMITY_DETECTED,
    VERIFIED_PRESENT,
    OUT_OF_RANGE,
    TOKEN_EXPIRED_OR_INVALID
}

data class BeaconFrame(
    val uuid: String = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
    val major: Int = 101, // Riyadh Main Campus
    val minor: Int = 204, // AI & Robotics Lecture Hall
    val txPowerDbm: Int = -59,
    val measuredRssi: Int = -68,
    val rollingNonce: String = "NONCE-94812",
    val timestamp: Long = System.currentTimeMillis()
)

data class AttendanceValidationResult(
    val studentId: String,
    val studentName: String,
    val campusId: Int,
    val hallId: Int,
    val estimatedDistanceMeters: Double,
    val status: AttendanceStatus,
    val verificationHash: String,
    val diagnosticMessage: String,
    val recordedAt: Long = System.currentTimeMillis()
)

class BleAttendanceBeaconEngine(
    private val maxAttendanceDistanceMeters: Double = 8.5,
    private val tokenValidityWindowMs: Long = 60_000L,
    private val defaultSecretSalt: String = "QALAM_KSA_EDTECH_2026"
) {
    private val verifiedRoster = mutableMapOf<String, AttendanceValidationResult>()

    fun estimateDistance(rssi: Int, txPowerDbm: Int = -59, pathLossExponent: Double = 2.2): Double {
        if (rssi == 0) return -1.0
        val ratio = (txPowerDbm - rssi).toDouble() / (10.0 * pathLossExponent)
        return (10.0.pow(ratio) * 100.0).toLong() / 100.0 // 2 decimal precision
    }

    fun computeVerificationHash(
        studentId: String,
        major: Int,
        minor: Int,
        nonce: String,
        salt: String = defaultSecretSalt
    ): String {
        val payload = "$studentId:$major:$minor:$nonce:$salt"
        val bytes = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }

    fun verifyAndRecordAttendance(
        studentId: String,
        studentName: String,
        beacon: BeaconFrame,
        clientChallengeHash: String,
        currentTime: Long = System.currentTimeMillis()
    ): AttendanceValidationResult {
        val distance = estimateDistance(beacon.measuredRssi, beacon.txPowerDbm)

        // 1. Check Proximity Distance
        if (distance < 0.0 || distance > maxAttendanceDistanceMeters) {
            val res = AttendanceValidationResult(
                studentId = studentId,
                studentName = studentName,
                campusId = beacon.major,
                hallId = beacon.minor,
                estimatedDistanceMeters = distance,
                status = AttendanceStatus.OUT_OF_RANGE,
                verificationHash = "REJECTED_DISTANCE",
                diagnosticMessage = "Student is outside lecture hall bounds (distance: ${distance}m > max ${maxAttendanceDistanceMeters}m)",
                recordedAt = currentTime
            )
            return res
        }

        // 2. Check Token Timestamp Age
        val tokenAge = currentTime - beacon.timestamp
        if (tokenAge > tokenValidityWindowMs || tokenAge < -5000L) {
            val res = AttendanceValidationResult(
                studentId = studentId,
                studentName = studentName,
                campusId = beacon.major,
                hallId = beacon.minor,
                estimatedDistanceMeters = distance,
                status = AttendanceStatus.TOKEN_EXPIRED_OR_INVALID,
                verificationHash = "TOKEN_EXPIRED",
                diagnosticMessage = "Beacon rolling token expired or invalid (age: ${tokenAge}ms)",
                recordedAt = currentTime
            )
            return res
        }

        // 3. Verify Anti-Spoofing Cryptographic Hash
        val expectedHash = computeVerificationHash(
            studentId = studentId,
            major = beacon.major,
            minor = beacon.minor,
            nonce = beacon.rollingNonce
        )

        if (clientChallengeHash.lowercase() != expectedHash.lowercase()) {
            val res = AttendanceValidationResult(
                studentId = studentId,
                studentName = studentName,
                campusId = beacon.major,
                hallId = beacon.minor,
                estimatedDistanceMeters = distance,
                status = AttendanceStatus.TOKEN_EXPIRED_OR_INVALID,
                verificationHash = "MISMATCH_DETECTED",
                diagnosticMessage = "Cryptographic signature mismatch. Spoofed check-in rejected.",
                recordedAt = currentTime
            )
            return res
        }

        // 4. Successful Verification
        val verifiedRecord = AttendanceValidationResult(
            studentId = studentId,
            studentName = studentName,
            campusId = beacon.major,
            hallId = beacon.minor,
            estimatedDistanceMeters = distance,
            status = AttendanceStatus.VERIFIED_PRESENT,
            verificationHash = expectedHash,
            diagnosticMessage = "Verified Present at Hall ${beacon.minor} (${distance}m, RSSI: ${beacon.measuredRssi} dBm)",
            recordedAt = currentTime
        )

        verifiedRoster[studentId] = verifiedRecord
        return verifiedRecord
    }

    fun getVerifiedCount(): Int = verifiedRoster.size

    fun getVerifiedRoster(): List<AttendanceValidationResult> = verifiedRoster.values.toList()

    fun resetSession() {
        verifiedRoster.clear()
    }
}
