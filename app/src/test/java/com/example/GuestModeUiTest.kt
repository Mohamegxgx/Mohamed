package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestModeUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetBefore() {
        runCatching { Firebase.auth.signOut() }
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.preferencesRepository.setGuestMode(false) }
    }

    @After
    fun resetAfter() {
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.preferencesRepository.setGuestMode(false) }
    }

    @Test
    fun mainActivity_guestModeEntersAppWithoutFirebaseUserAndOffersGoogleUpgradeInSettings() {
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("continue_as_guest_btn").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("continue_as_guest_btn").performScrollTo().assertIsDisplayed().performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("dashboard_lazy_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("fab_quick_add").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_settings_btn").performClick()

        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("guest_mode_settings_card").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("guest_sign_in_google_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_guest_mode_btn").assertIsDisplayed()
        assertTrue(composeTestRule.onAllNodesWithTag("sync_to_cloud_btn").fetchSemanticsNodes().isEmpty())
    }
}
