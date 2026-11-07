package sa.factory.qalamcampussmarted.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sa.factory.qalamcampussmarted.engine.*
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver {
    val peripheral = "Smart Stylus Digitizer (4096-Level) + BLE Attendance Beacon"
    val stylusEngine = SmartStylusDigitizerEngine()
    val beaconEngine = BleAttendanceBeaconEngine()

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "BLE GATT 0x180A & Stylus HID Driver Handshake initializing..."))
        delay(200)

        // Seed initial lecture hall session
        val beacon = BeaconFrame(
            uuid = "E2C56DB5-DFFB-48D2-B060-D0F5A71096E0",
            major = 101, // Campus Riyadh
            minor = 204, // Smart Lab 204
            txPowerDbm = -59,
            measuredRssi = -64,
            rollingNonce = "NONCE-8472"
        )
        val initialHash = beaconEngine.computeVerificationHash("STU-2026-01", beacon.major, beacon.minor, beacon.rollingNonce)
        val initialAtt = beaconEngine.verifyAndRecordAttendance("STU-2026-01", "أحمد السبيعي", beacon, initialHash)

        val initPayload = "[ONLINE] Lab 204 | Beacon: " + initialAtt.status.name + " (" + initialAtt.estimatedDistanceMeters + "m) | " +
                "Stylus: 4096 Levels Active | Palm Rejection Filter: READY"
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", initPayload))

        var frameCounter = 1
        var prevStroke: ProcessedStrokePoint? = null

        val sampleStudents = listOf(
            Pair("STU-2026-02", "سارة القحطاني"),
            Pair("STU-2026-03", "محمد الدوسري"),
            Pair("STU-2026-04", "نورة الغامدي"),
            Pair("STU-2026-05", "فيصل العتيبي")
        )

        while (true) {
            delay(1500)
            frameCounter++
            val jitter = Random.nextInt(1, 8)

            val liveStatus = if (frameCounter % 2 == 0) {
                // Stylus stroke telemetry cycle
                val rawPressure = Random.nextInt(500, 3800)
                val tiltX = Random.nextInt(10, 45).toFloat()
                val tiltY = Random.nextInt(5, 30).toFloat()
                val packet = stylusEngine.createSyntheticPacket(
                    pressure = rawPressure,
                    tiltX = tiltX,
                    tiltY = tiltY,
                    contactRadiusMm = 2.4f,
                    buttonMask = 0
                )
                val stroke = stylusEngine.processStylusPacket(packet, prevStroke, dx = 12f, dy = 18f)
                prevStroke = stroke

                val pressureFmt = String.format("%.2f", stroke.smoothedPressure * 100f)
                val velFmt = String.format("%.0f", stroke.velocityPixelsPerSec)
                "[STYLUS_ACTIVE] Frame #" + frameCounter + " (+" + jitter + "ms) | " +
                        "P: " + stroke.rawPressure + "/4096 (" + pressureFmt + "%) | " +
                        "Tilt: " + String.format("%.1f", stroke.tiltAltitudeDeg) + " deg | " +
                        "Vel: " + velFmt + " px/s | Palm: " + (if (stroke.isPalmRejected) "REJECTED" else "VALID")
            } else {
                // BLE Attendance cycle
                val stuIndex = (frameCounter / 2) % sampleStudents.size
                val stu = sampleStudents[stuIndex]
                val rssi = Random.nextInt(-75, -55)
                val studentBeacon = BeaconFrame(
                    major = 101,
                    minor = 204,
                    txPowerDbm = -59,
                    measuredRssi = rssi,
                    rollingNonce = "NONCE-" + (8472 + frameCounter)
                )
                val hash = beaconEngine.computeVerificationHash(stu.first, studentBeacon.major, studentBeacon.minor, studentBeacon.rollingNonce)
                val attRes = beaconEngine.verifyAndRecordAttendance(stu.first, stu.second, studentBeacon, hash)

                "[BEACON_ATTENDANCE] Hall 204 | Student: " + attRes.studentId + " (" + attRes.studentName + ") | " +
                        "Dist: " + attRes.estimatedDistanceMeters + "m | Roster: " + beaconEngine.getVerifiedCount() + " Present"
            }

            emit(HardwareTelemetryFrame(peripheral, "ACTIVE_STREAMING", liveStatus))
        }
    }
}
