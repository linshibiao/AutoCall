package com.autocall.app.call

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural regression checks for test-call flag propagation and forceSimDial wiring.
 *
 * Full service/Telecom instrumentation is not configured here, so these tests assert
 * source contracts (same style as [ScreenTimeoutWireInTest] / DashboardScreenDeleteUiTest).
 */
class TestCallAndSimDialWireInTest {

    private val triggerSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/service/CallTriggerService.kt")
            .readText()
    }

    private val launcherSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/call/CallLauncher.kt")
            .readText()
    }

    private val resolverSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/call/SimDialAccountResolver.kt")
            .readText()
    }

    private val appSettingsSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/data/AppSettings.kt")
            .readText()
    }

    private val migrationsSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/data/Migrations.kt")
            .readText()
    }

    private val alarmSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/scheduler/AlarmScheduler.kt")
            .readText()
    }

    private val alarmReceiverSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/receiver/CallAlarmReceiver.kt")
            .readText()
    }

    private val busyDeferReceiverSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/receiver/BusyDeferActionReceiver.kt")
            .readText()
    }

    private val busyDeferNotifierSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/call/BusyDeferNotifier.kt")
            .readText()
    }

    private val retrySource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/call/CallRetryCoordinator.kt")
            .readText()
    }

    private val viewModelSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/ui/AutoCallViewModel.kt")
            .readText()
    }

    private val appSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/ui/AutoCallApp.kt")
            .readText()
    }

    private val settingsDialogSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/ui/SettingsDialog.kt")
            .readText()
    }

    @Test
    fun callTriggerService_resolvesTreatAsTestViaFlagLogic() {
        assertTrue(
            "CallTriggerService must use TestCallFlagLogic.treatAsTest",
            triggerSource.contains("TestCallFlagLogic.treatAsTest("),
        )
        assertTrue(
            "placeCall must read global AppSettings forceSimDial",
            triggerSource.contains("forceSimDial = AppSettings(this).isForceSimDial()"),
        )
        assertTrue(
            "busy defer must pass treatAsTest into scheduleRetry",
            triggerSource.contains("isTestCall = isTestCall") &&
                triggerSource.contains("scheduleRetry("),
        )
        assertTrue(
            "beginSession must receive treatAsTest",
            triggerSource.contains("isTestCall = treatAsTest"),
        )
    }

    @Test
    fun callLauncher_pinsSimAccountOnlyWhenForceSimDial() {
        assertTrue(
            launcherSource.contains("forceSimDial: Boolean = false"),
        )
        assertTrue(
            launcherSource.contains("SimDialAccountResolver.resolveSimHandle(appContext)"),
        )
        assertTrue(
            "SIM handle must only be resolved when forceSimDial is true",
            launcherSource.contains("val simHandle = if (forceSimDial)"),
        )
        assertTrue(
            "null SIM resolve must still place without aborting (fail-open)",
            launcherSource.contains(
                "forceSimDial set but no SIM account resolved; placing without account pin",
            ),
        )
        assertFalse(
            "fail-open: must not abort placeCall when SIM resolve is null",
            launcherSource.contains("aborting placeCall") ||
                launcherSource.contains("no SIM account found; aborting"),
        )
        assertTrue(
            "SIM bypass must use gateway extras to skip user CallRedirectionService",
            launcherSource.contains("skipUserCallRedirection = true") &&
                launcherSource.contains("LEGACY_GATEWAY_URI") &&
                launcherSource.contains("com.android.phone.extra.GATEWAY_URI") &&
                launcherSource.contains("GatewayUriLogic.legacyGatewayUriString(uri)"),
        )
        assertTrue(
            "forceSimDial path must prefer telecom.placeCall before ACTION_CALL",
            launcherSource.contains("placeCallViaTelecom(") &&
                launcherSource.contains("telecomManager.placeCall(uri, extras)"),
        )
        val forceSimBlock = launcherSource.substringAfter(
            "// Prefer TelecomManager.placeCall",
            missingDelimiterValue = "",
        )
        assertTrue("forceSimDial prefer-telecom comment/block must exist", forceSimBlock.isNotEmpty())
        val viaTelecomIdx = forceSimBlock.indexOf("placeCallViaTelecom(")
        val viaDialerIdx = forceSimBlock.indexOf("placeCallViaActionCall(")
        assertTrue("placeCallViaTelecom must appear in forceSimDial path", viaTelecomIdx >= 0)
        assertTrue("placeCallViaActionCall must appear as fallback", viaDialerIdx >= 0)
        assertTrue(
            "telecom.placeCall path must be attempted before ACTION_CALL fallback",
            viaTelecomIdx < viaDialerIdx,
        )
        assertTrue(
            "ACTION_CALL fallback must target default dialer to avoid Voice chooser",
            launcherSource.contains("defaultDialerPackage") &&
                launcherSource.contains("setPackage(dialerPackage)"),
        )
        assertTrue(
            launcherSource.contains("TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE"),
        )
    }

    @Test
    fun callRetryCoordinator_isLineBusy_consultsTelecomIsInCall() {
        assertTrue(
            "isLineBusy must be defined",
            retrySource.contains("fun isLineBusy(context: Context)"),
        )
        assertTrue(
            "isLineBusy must consult Telecom after TelephonyManager idle",
            retrySource.contains("return isTelecomInCall(appContext)"),
        )
        assertTrue(
            "isTelecomInCall must call TelecomManager.isInCall",
            retrySource.contains("private fun isTelecomInCall(context: Context)") &&
                retrySource.contains("telecom.isInCall"),
        )
        val isLineBusyIdx = retrySource.indexOf("fun isLineBusy(context: Context)")
        val telecomInCallIdx = retrySource.indexOf("isTelecomInCall(appContext)", isLineBusyIdx)
        assertTrue("isLineBusy must call isTelecomInCall", telecomInCallIdx > isLineBusyIdx)
        assertTrue(
            "shouldDeferDial must pass isLineBusy into BusyDeferLogic",
            retrySource.contains("lineBusy = isLineBusy(appContext)"),
        )
    }

    @Test
    fun simDialAccountResolver_fallsBackToDefaultOutgoingTelAccount() {
        assertTrue(
            "must query default tel outgoing account",
            resolverSource.contains(
                "telecom.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)",
            ),
        )
        assertTrue(
            "default-outgoing failures must be logged",
            resolverSource.contains("\"getDefaultOutgoingPhoneAccount failed\""),
        )
        assertTrue(
            "callCapablePhoneAccounts failures must be logged",
            resolverSource.contains("\"callCapablePhoneAccounts failed\""),
        )
        assertTrue(
            "empty/filtered callCapable list must fall back to default outgoing",
            resolverSource.contains("callCapablePhoneAccounts is empty/filtered") ||
                (
                    resolverSource.contains("defaultCandidate") &&
                        resolverSource.contains(
                            "SimDialAccountLogic.looksLikeTelephony(defaultCandidate)",
                        )
                    ),
        )
        assertTrue(
            "fallback default must pass looksLikeTelephony before return",
            resolverSource.contains(
                "SimDialAccountLogic.looksLikeTelephony(defaultCandidate)",
            ),
        )
        assertTrue(
            "fallback must accept handle when getPhoneAccount is filtered",
            resolverSource.contains("minimalCandidate(handle)"),
        )
        assertTrue(
            "no-match path must log and return null",
            resolverSource.contains("No SIM/telephony account matched") &&
                resolverSource.contains("return null"),
        )
        val selectIdx = resolverSource.indexOf("SimDialAccountLogic.selectSimAccount(")
        val looksLikeIdx = resolverSource.indexOf(
            "SimDialAccountLogic.looksLikeTelephony(defaultCandidate)",
        )
        assertTrue("selectSimAccount must be present", selectIdx >= 0)
        assertTrue("looksLikeTelephony fallback must be present", looksLikeIdx >= 0)
        assertTrue(
            "default-outgoing looksLikeTelephony fallback must run after selectSimAccount",
            looksLikeIdx > selectIdx,
        )
    }

    @Test
    fun forceSimDial_livesInAppSettingsPrefs_notRoomMigration() {
        assertTrue(
            appSettingsSource.contains("KEY_FORCE_SIM_DIAL = \"force_sim_dial\""),
        )
        assertTrue(
            appSettingsSource.contains("prefs.getBoolean(KEY_FORCE_SIM_DIAL, false)"),
        )
        assertTrue(
            appSettingsSource.contains("prefs.edit().putBoolean(KEY_FORCE_SIM_DIAL, enabled)"),
        )
        assertFalse(
            "forceSimDial must not be introduced via Room migrations",
            migrationsSource.contains("force_sim_dial") ||
                migrationsSource.contains("forceSimDial"),
        )
    }

    @Test
    fun alarmScheduler_putsIsTestCallOnRetryIntent() {
        assertTrue(
            alarmSource.contains("putExtra(CallAlarmReceiver.EXTRA_TEST_CALL, isTestCall)"),
        )
        assertTrue(
            alarmSource.contains("isTestCall: Boolean = false"),
        )
        assertTrue(
            "scheduleRetry must forward isTestCall into retryPendingIntent",
            alarmSource.contains("isTestCall = isTestCall"),
        )
    }

    @Test
    fun callAlarmReceiver_forwardsIsTestCallToService() {
        assertTrue(
            alarmReceiverSource.contains("EXTRA_TEST_CALL = \"extra_test_call\""),
        )
        assertTrue(
            alarmReceiverSource.contains(
                "val isTestCall = intent.getBooleanExtra(EXTRA_TEST_CALL, false)",
            ),
        )
        assertTrue(
            alarmReceiverSource.contains("isTestCall = isTestCall"),
        )
    }

    @Test
    fun busyDeferPath_propagatesIsTestCall() {
        assertTrue(
            busyDeferNotifierSource.contains(
                "putExtra(BusyDeferActionReceiver.EXTRA_TEST_CALL, state.isTestCall)",
            ),
        )
        assertTrue(
            busyDeferReceiverSource.contains(
                "val isTestCall = intent.getBooleanExtra(EXTRA_TEST_CALL, false)",
            ),
        )
        assertTrue(
            "Place must OR Intent flag with store isTestCall before clear",
            busyDeferReceiverSource.contains("BusyDeferStore.load(appContext, scheduledCallId)") &&
                busyDeferReceiverSource.contains(
                    "val treatAsTest = isTestCall || storedIsTestCall",
                ) &&
                busyDeferReceiverSource.contains("isTestCall = treatAsTest"),
        )
        val loadIdx = busyDeferReceiverSource.indexOf(
            "BusyDeferStore.load(appContext, scheduledCallId)",
        )
        val clearIdx = busyDeferReceiverSource.indexOf(
            "BusyDeferStore.clear(appContext, scheduledCallId)",
        )
        assertTrue("load before clear on Place", loadIdx >= 0 && clearIdx > loadIdx)
    }

    @Test
    fun callRetryCoordinator_passesSessionIsTestCallToScheduleRetry() {
        assertTrue(
            retrySource.contains("isTestCall = next.isTestCall"),
        )
        assertTrue(
            "beginSession must persist isTestCall on the watch session",
            retrySource.contains("isTestCall = isTestCall"),
        )
        assertTrue(
            "test dials must start retry deadline from now",
            retrySource.contains("originalScheduleMillis = if (isTestCall)"),
        )
    }

    @Test
    fun viewModel_testCallStartsServiceWithIsTestCallTrue() {
        assertTrue(
            viewModelSource.contains("fun testCall(scheduledCall: ScheduledCall)"),
        )
        assertTrue(
            viewModelSource.contains("CallTriggerService.start("),
        )
        assertTrue(
            viewModelSource.contains("isTestCall = true"),
        )
        assertTrue(
            "settings must persist global forceSimDial",
            viewModelSource.contains("appSettings.setForceSimDial(forceSimDial)"),
        )
    }

    @Test
    fun settingsDialog_exposesGlobalForceSimDialToggle() {
        assertTrue(
            settingsDialogSource.contains("forceSimDial: Boolean"),
        )
        assertTrue(
            settingsDialogSource.contains("""Text("Dial via SIM (bypass Google Voice)")"""),
        )
        assertTrue(
            appSource.contains("forceSimDial = forceSimDial"),
        )
        assertTrue(
            appSource.contains("viewModel.updateCallSettings("),
        )
    }

    @Test
    fun passThroughCallRedirectionService_placesUnmodified() {
        val serviceSource = resolveSource(
            "app/src/main/java/com/autocall/app/call/PassThroughCallRedirectionService.kt",
        ).readText()
        val manifestSource = resolveSource("app/src/main/AndroidManifest.xml").readText()
        assertTrue(serviceSource.contains("placeCallUnmodified()"))
        assertTrue(
            manifestSource.contains(".call.PassThroughCallRedirectionService") &&
                manifestSource.contains("android.telecom.CallRedirectionService"),
        )
    }

    private fun resolveSource(relativeFromRepoRoot: String): File {
        val candidates = listOf(
            File(relativeFromRepoRoot),
            File("../$relativeFromRepoRoot"),
            File(System.getProperty("user.dir"), relativeFromRepoRoot),
            File(System.getProperty("user.dir"), "../$relativeFromRepoRoot"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error(
                "Could not locate $relativeFromRepoRoot (cwd=${System.getProperty("user.dir")})",
            )
    }
}
