package com.autocall.app.call

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural regression checks for screen-timeout acquire/release wiring.
 *
 * Full service/coordinator instrumentation is not configured here, so these
 * tests assert source contracts (same style as DashboardScreenDeleteUiTest).
 */
class ScreenTimeoutWireInTest {

    private val triggerSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/service/CallTriggerService.kt")
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

    @Test
    fun callTriggerService_acquiresBeforePlaceCall() {
        val acquireIdx = triggerSource.indexOf("ScreenTimeoutHelper.acquire(this)")
        val placeCallIdx = triggerSource.indexOf("CallLauncher.placeCall(")
        assertTrue("ScreenTimeoutHelper.acquire must be present", acquireIdx >= 0)
        assertTrue("CallLauncher.placeCall must be present", placeCallIdx >= 0)
        assertTrue(
            "acquire must run before placeCall so the screen stays awake during dial",
            acquireIdx < placeCallIdx,
        )
    }

    @Test
    fun callTriggerService_releasesOnPlacementFailureWithoutWatchSession() {
        assertTrue(
            triggerSource.contains("ScreenTimeoutHelper.release(this)"),
        )
        assertTrue(
            triggerSource.contains("ScreenTimeoutHelper.releaseWhenCallCompletes(this)"),
        )
    }

    @Test
    fun callRetryCoordinator_releasesOnAbortAndFinish() {
        assertTrue(
            "abort() must release screen timeout",
            retrySource.contains("ScreenTimeoutHelper.release(appContext)"),
        )
        assertTrue(
            "finishCampaignLocked must release screen timeout",
            retrySource.contains("private fun finishCampaignLocked") &&
                retrySource.substringAfter("private fun finishCampaignLocked")
                    .substringBefore("private fun ")
                    .contains("ScreenTimeoutHelper.release(appContext)"),
        )
    }

    @Test
    fun callRetryCoordinator_syncsHoldWithWatchSessionOnRestore() {
        assertTrue(
            retrySource.contains(
                "ScreenTimeoutHelper.syncWithWatchSession(appContext, hasActiveWatchSession = hasSession)",
            ),
        )
    }

    @Test
    fun systemStatus_refreshIncludesCanWriteSettings() {
        assertTrue(
            viewModelSource.contains(
                "canWriteSettings = PermissionHelper.canWriteSettings(context)",
            ),
        )
        assertTrue(
            viewModelSource.contains("!canWriteSettings"),
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
