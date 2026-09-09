package com.autocall.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Structural regression checks for scheduled-call delete UX.
 *
 * Compose UI testing (androidx.compose.ui:ui-test-junit4) is not configured in this
 * module, so these tests assert source contracts instead of driving gestures.
 */
class DashboardScreenDeleteUiTest {

    private val dashboardSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/ui/DashboardScreen.kt")
            .readText()
    }

    private val appSource: String by lazy {
        resolveSource("app/src/main/java/com/autocall/app/ui/AutoCallApp.kt")
            .readText()
    }

    @Test
    fun dashboard_doesNotUseSwipeToDismissDelete() {
        assertFalse(
            "SwipeToDismissBox must not be used for scheduled call cards",
            dashboardSource.contains("SwipeToDismissBox"),
        )
        assertFalse(
            "rememberSwipeToDismissBoxState must not be used",
            dashboardSource.contains("rememberSwipeToDismissBoxState"),
        )
        assertFalse(
            "SwipeToDismissBoxValue must not be used",
            dashboardSource.contains("SwipeToDismissBoxValue"),
        )
        assertFalse(
            "SwipeToDeleteCallCard wrapper must remain removed",
            dashboardSource.contains("SwipeToDeleteCallCard"),
        )
    }

    @Test
    fun dashboard_deletesOnlyViaTrashIconButton() {
        assertTrue(
            "Delete IconButton with contentDescription must remain",
            dashboardSource.contains("""contentDescription = "Delete""""),
        )
        assertTrue(
            "Trash delete must use Icons.Default.Delete",
            dashboardSource.contains("Icons.Default.Delete"),
        )
        assertTrue(
            "Delete IconButton must invoke onDeleteClick",
            dashboardSource.contains("IconButton(onClick = onDeleteClick)"),
        )
        assertTrue(
            "List items must render ScheduledCallCard directly (not a swipe wrapper)",
            dashboardSource.contains("ScheduledCallCard("),
        )
    }

    @Test
    fun autoCallApp_wiresDeleteToViewModel() {
        assertTrue(
            "Dashboard onDeleteClick must call viewModel.deleteCall",
            appSource.contains("onDeleteClick = viewModel::deleteCall"),
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
