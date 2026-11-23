package com.autocall.app.call

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
            launcherSource.contains("SimDialAccountResolver.resolveSimHandle(context)"),
        )
        assertTrue(
            "SIM handle must only be resolved when forceSimDial is true",
            launcherSource.contains("val simHandle = if (forceSimDial)"),
        )
        assertTrue(
            "null SIM resolve must fail closed (abort placeCall)",
            launcherSource.contains(
                "forceSimDial set but no SIM account found; aborting placeCall",
            ),
        )
        assertTrue(
            "forceSimDial without SIM handle must return false",
            launcherSource.contains("if (forceSimDial && simHandle == null)"),
        )
        assertTrue(
            launcherSource.contains("TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE"),
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
                busyDeferReceiverSource.contains("isTestCall = treatAsTest"),
        )
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
