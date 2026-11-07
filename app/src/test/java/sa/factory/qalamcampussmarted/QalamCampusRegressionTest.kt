package sa.factory.qalamcampussmarted

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import sa.factory.qalamcampussmarted.data.repository.QalamCampusRepositoryImpl
import sa.factory.qalamcampussmarted.engine.*
import sa.factory.qalamcampussmarted.hardware.HardwareSensorDriver
import sa.factory.qalamcampussmarted.model.QalamCampusItem

class QalamCampusRegressionTest {

    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val sensorDriver = HardwareSensorDriver()
        val repository = QalamCampusRepositoryImpl(sensorDriver)

        val item1 = QalamCampusItem("t1", "خدمة تجريبية أ", "الوصف", 100.0, "معتمد", "عام")
        val item2 = QalamCampusItem("t2", "خدمة تجريبية ب", "الوصف", 200.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada EMV")

        assertEquals(300.0, order.subtotalSar, 0.01)
        assertEquals(45.0, order.vatSar, 0.01)
        assertEquals(345.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    @Test
    fun testStylusPressureNormalizationAndClamping() {
        val engine = SmartStylusDigitizerEngine()

        val zeroPkt = engine.createSyntheticPacket(pressure = 0)
        val zeroPt = engine.processStylusPacket(zeroPkt)
        assertEquals(0.0f, zeroPt.normalizedPressure, 0.001f)

        val midPkt = engine.createSyntheticPacket(pressure = 2048)
        val midPt = engine.processStylusPacket(midPkt)
        assertEquals(0.5f, midPt.normalizedPressure, 0.01f)

        val maxPkt = engine.createSyntheticPacket(pressure = 4096)
        val maxPt = engine.processStylusPacket(maxPkt)
        assertEquals(1.0f, maxPt.normalizedPressure, 0.001f)

        val overflowPkt = engine.createSyntheticPacket(pressure = 5000)
        val overflowPt = engine.processStylusPacket(overflowPkt)
        assertEquals(4096, overflowPt.rawPressure)
        assertEquals(1.0f, overflowPt.normalizedPressure, 0.001f)
    }

    @Test
    fun testStylusPalmRejectionHeuristic() {
        val engine = SmartStylusDigitizerEngine(palmRadiusThresholdMm = 7.5f)

        // Palm resting: large radius (9.0mm) + low resting pressure (150) -> REJECTED
        val palmPkt = engine.createSyntheticPacket(pressure = 150, contactRadiusMm = 9.0f)
        val palmPt = engine.processStylusPacket(palmPkt)
        assertTrue(palmPt.isPalmRejected)

        // Sub-threshold ghost contact: pressure < 32 -> REJECTED
        val ghostPkt = engine.createSyntheticPacket(pressure = 10, contactRadiusMm = 2.0f)
        val ghostPt = engine.processStylusPacket(ghostPkt)
        assertTrue(ghostPt.isPalmRejected)

        // Legitimate fine stylus nib: small radius (2.0mm) + intentional stroke (1500) -> VALID
        val validPkt = engine.createSyntheticPacket(pressure = 1500, contactRadiusMm = 2.0f)
        val validPt = engine.processStylusPacket(validPkt)
        assertFalse(validPt.isPalmRejected)
    }

    @Test
    fun testStylusTiltCalculation() {
        val engine = SmartStylusDigitizerEngine()

        val pkt = engine.createSyntheticPacket(pressure = 1000, tiltX = 30f, tiltY = 0f)
        val pt = engine.processStylusPacket(pkt)

        assertEquals(0.0f, pt.tiltAzimuthDeg, 1.0f)
        assertEquals(60.0f, pt.tiltAltitudeDeg, 1.0f)
    }

    @Test
    fun testStylusPressureSmoothingEMA() {
        val engine = SmartStylusDigitizerEngine(smoothingAlpha = 0.5f)

        val p1 = engine.createSyntheticPacket(pressure = 1000)
        val pt1 = engine.processStylusPacket(p1)

        val p2 = engine.createSyntheticPacket(pressure = 3000)
        val pt2 = engine.processStylusPacket(p2, prevPoint = pt1)

        // Expected: 0.5 * (3000/4096) + 0.5 * (1000/4096) = 2000 / 4096 = 0.488
        val expected = 0.5f * (3000f / 4096f) + 0.5f * (1000f / 4096f)
        assertEquals(expected, pt2.smoothedPressure, 0.01f)
    }

    @Test
    fun testBleBeaconDistanceEstimation() {
        val engine = BleAttendanceBeaconEngine()

        // Near beacon: measured RSSI close to TxPower (-59 dBm)
        val distClose = engine.estimateDistance(rssi = -59, txPowerDbm = -59)
        assertEquals(1.0, distClose, 0.1)

        // Far beacon: RSSI = -75 dBm -> approx 5-6 meters
        val distFar = engine.estimateDistance(rssi = -75, txPowerDbm = -59)
        assertTrue(distFar > 3.0 && distFar < 8.0)
    }

    @Test
    fun testBleBeaconAntiSpoofingHashVerification() {
        val engine = BleAttendanceBeaconEngine(maxAttendanceDistanceMeters = 10.0)

        val beacon = BeaconFrame(
            major = 101,
            minor = 204,
            txPowerDbm = -59,
            measuredRssi = -65,
            rollingNonce = "NONCE-TEST-7788"
        )

        // Valid signature match
        val validHash = engine.computeVerificationHash("STU-1001", beacon.major, beacon.minor, beacon.rollingNonce)
        val resultValid = engine.verifyAndRecordAttendance("STU-1001", "عبدالله الشهري", beacon, validHash)

        assertEquals(AttendanceStatus.VERIFIED_PRESENT, resultValid.status)
        assertEquals(1, engine.getVerifiedCount())

        // Spoofed/tampered hash
        val resultTampered = engine.verifyAndRecordAttendance("STU-1002", "خالد الحربي", beacon, "INVALID_HASH_99")
        assertEquals(AttendanceStatus.TOKEN_EXPIRED_OR_INVALID, resultTampered.status)
        assertEquals(1, engine.getVerifiedCount())
    }

    @Test
    fun testBleBeaconProximityCutoff() {
        val engine = BleAttendanceBeaconEngine(maxAttendanceDistanceMeters = 5.0)

        // Very weak signal indicating distance > 10m
        val farBeacon = BeaconFrame(
            major = 101,
            minor = 204,
            txPowerDbm = -59,
            measuredRssi = -90,
            rollingNonce = "NONCE-FAR"
        )

        val validHash = engine.computeVerificationHash("STU-1003", farBeacon.major, farBeacon.minor, farBeacon.rollingNonce)
        val result = engine.verifyAndRecordAttendance("STU-1003", "فهد المطيري", farBeacon, validHash)

        assertEquals(AttendanceStatus.OUT_OF_RANGE, result.status)
    }

    @Test
    fun testHardwareSensorDriverInitialStream() = runBlocking {
        val driver = HardwareSensorDriver()
        val frames = driver.observePeripheralStream().take(2).toList()

        assertEquals(2, frames.size)
        assertEquals("CONNECTING", frames[0].connectionState)
        assertEquals("ONLINE", frames[1].connectionState)
        assertTrue(frames[1].livePayload.contains("Lab 204"))
        assertTrue(frames[1].livePayload.contains("Stylus: 4096 Levels"))
    }
}
