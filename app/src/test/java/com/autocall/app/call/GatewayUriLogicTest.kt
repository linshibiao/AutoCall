package com.autocall.app.call

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GatewayUriLogicTest {

    @Test
    fun legacyGatewayUriString_encodesHash_soParseKeepsFullBridgeSsp() {
        val dial = Uri.fromParts("tel", "18005551212,,,,*,,,1234,,,,#", null)
        val legacy = GatewayUriLogic.legacyGatewayUriString(dial)
        assertFalse(
            "legacy string must not leave a raw # that Uri.parse treats as fragment",
            legacy.contains("#"),
        )
        val reparsed = Uri.parse(legacy)
        assertEquals("tel", reparsed.scheme)
        assertEquals("18005551212,,,,*,,,1234,,,,#", reparsed.schemeSpecificPart)
        assertEquals(null, reparsed.fragment)
    }

    @Test
    fun legacyGatewayUriString_encodesExistingPercentBeforeHash() {
        val dial = Uri.fromParts("tel", "12%34#5", null)
        val legacy = GatewayUriLogic.legacyGatewayUriString(dial)
        val reparsed = Uri.parse(legacy)
        assertEquals("12%34#5", reparsed.schemeSpecificPart)
    }

    @Test
    fun legacyGatewayUriString_leavesPlainNumbersUnchangedAsideFromScheme() {
        val dial = Uri.fromParts("tel", "18005551212", null)
        assertEquals("tel:18005551212", GatewayUriLogic.legacyGatewayUriString(dial))
    }
}
