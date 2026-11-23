package com.autocall.app.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimDialAccountLogicTest {

    @Test
    fun selectSimAccount_prefersSimSubscriptionCapability() {
        val sim = SimDialAccountLogic.AccountCandidate(
            id = "sim",
            packageName = "com.android.phone",
            isSimSubscription = true,
        )
        val voice = SimDialAccountLogic.AccountCandidate(
            id = "gv",
            packageName = "com.google.android.apps.googlevoice",
            isSimSubscription = false,
        )
        assertEquals(
            "sim",
            SimDialAccountLogic.selectSimAccount(listOf(voice, sim))?.id,
        )
    }

    @Test
    fun selectSimAccount_skipsGoogleVoice_andPicksTelephonyPackage() {
        val voice = SimDialAccountLogic.AccountCandidate(
            id = "gv",
            packageName = "com.google.android.apps.googlevoice",
        )
        val telephony = SimDialAccountLogic.AccountCandidate(
            id = "tel",
            packageName = "com.android.phone",
        )
        assertEquals(
            "tel",
            SimDialAccountLogic.selectSimAccount(listOf(voice, telephony))?.id,
        )
    }

    @Test
    fun selectSimAccount_prefersDefaultOutgoing_amongTelephonyAccounts() {
        val sim1 = SimDialAccountLogic.AccountCandidate(
            id = "sim1",
            packageName = "com.android.phone",
            label = "SIM 1",
        )
        val sim2 = SimDialAccountLogic.AccountCandidate(
            id = "sim2",
            packageName = "com.android.phone",
            label = "SIM 2",
        )
        assertEquals(
            "sim2",
            SimDialAccountLogic.selectSimAccount(
                candidates = listOf(sim1, sim2),
                defaultOutgoingId = "sim2",
            )?.id,
        )
    }

    @Test
    fun selectSimAccount_returnsNull_whenOnlyOverTheTopAccounts() {
        assertNull(
            SimDialAccountLogic.selectSimAccount(
                listOf(
                    SimDialAccountLogic.AccountCandidate(
                        id = "gv",
                        packageName = "com.google.android.apps.googlevoice",
                    ),
                ),
            ),
        )
    }

    @Test
    fun selectSimAccount_ignoresDisabledAccounts() {
        val disabledSim = SimDialAccountLogic.AccountCandidate(
            id = "sim",
            packageName = "com.android.phone",
            isSimSubscription = true,
            isEnabled = false,
        )
        val enabledTel = SimDialAccountLogic.AccountCandidate(
            id = "tel2",
            packageName = "com.android.phone",
            isEnabled = true,
        )
        assertEquals(
            "tel2",
            SimDialAccountLogic.selectSimAccount(listOf(disabledSim, enabledTel))?.id,
        )
    }

    @Test
    fun isLikelyOverTheTop_detectsGoogleVoice() {
        assertTrue(
            SimDialAccountLogic.isLikelyOverTheTop("com.google.android.apps.googlevoice"),
        )
        assertFalse(SimDialAccountLogic.isLikelyOverTheTop("com.android.phone"))
    }

    @Test
    fun selectSimAccount_returnsNull_whenEmptyOrAllDisabled() {
        assertNull(SimDialAccountLogic.selectSimAccount(emptyList()))
        assertNull(
            SimDialAccountLogic.selectSimAccount(
                listOf(
                    SimDialAccountLogic.AccountCandidate(
                        id = "sim",
                        packageName = "com.android.phone",
                        isSimSubscription = true,
                        isEnabled = false,
                    ),
                ),
            ),
        )
    }

    @Test
    fun selectSimAccount_returnsNull_whenNoSimMatched_callerMustFailClosed() {
        // CallLauncher must abort placeCall when forceSimDial is on and resolve returns null.
        assertNull(
            SimDialAccountLogic.selectSimAccount(
                listOf(
                    SimDialAccountLogic.AccountCandidate(
                        id = "gv",
                        packageName = "com.google.android.apps.googlevoice",
                    ),
                    SimDialAccountLogic.AccountCandidate(
                        id = "wa",
                        packageName = "com.whatsapp",
                    ),
                ),
            ),
        )
    }

    @Test
    fun looksLikeTelephony_rejectsOttEvenWhenLabelLooksLikeMobile() {
        assertFalse(
            SimDialAccountLogic.looksLikeTelephony(
                SimDialAccountLogic.AccountCandidate(
                    id = "gv",
                    packageName = "com.google.android.apps.googlevoice",
                    label = "Google Voice Mobile",
                ),
            ),
        )
    }

    @Test
    fun looksLikeTelephony_matchesLabelAndSamsungHeuristics() {
        assertTrue(
            SimDialAccountLogic.looksLikeTelephony(
                SimDialAccountLogic.AccountCandidate(
                    id = "oem",
                    packageName = "com.carrier.app",
                    label = "SIM 1 Mobile",
                ),
            ),
        )
        assertTrue(
            SimDialAccountLogic.looksLikeTelephony(
                SimDialAccountLogic.AccountCandidate(
                    id = "sam",
                    packageName = "com.samsung.android.phone",
                    className = "com.samsung.android.phone.PhoneConnectionService",
                ),
            ),
        )
        assertFalse(
            SimDialAccountLogic.looksLikeTelephony(
                SimDialAccountLogic.AccountCandidate(
                    id = "gv",
                    packageName = "com.google.android.apps.googlevoice",
                ),
            ),
        )
    }

    @Test
    fun selectSimAccount_prefersDefaultOutgoingAmongSimSubscriptionAccounts() {
        val sim1 = SimDialAccountLogic.AccountCandidate(
            id = "sim1",
            packageName = "com.android.phone",
            isSimSubscription = true,
        )
        val sim2 = SimDialAccountLogic.AccountCandidate(
            id = "sim2",
            packageName = "com.android.phone",
            isSimSubscription = true,
        )
        assertEquals(
            "sim1",
            SimDialAccountLogic.selectSimAccount(
                candidates = listOf(sim1, sim2),
                defaultOutgoingId = "sim1",
            )?.id,
        )
    }
}
