package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthGateUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun signOutBeforeLaunch() {
        runCatching { Firebase.auth.signOut() }
        val app = ApplicationProvider.getApplicationContext<ElbagorryMoneyApp>()
        runBlocking { app.preferencesRepository.setGuestMode(false) }
    }

    @Test
    fun mainActivity_whenSignedOut_rendersGoogleSignInGateAndBlocksLedger() {
        composeTestRule.waitUntil(5_000L) {
            composeTestRule.onAllNodesWithTag("google_sign_in_button")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("google_sign_in_button").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("fab_quick_add").fetchSemanticsNodes().let { nodes ->
            org.junit.Assert.assertTrue("FAB must not render while signed out", nodes.isEmpty())
        }
        composeTestRule.onAllNodesWithTag("dashboard_lazy_list").fetchSemanticsNodes().let { nodes ->
            org.junit.Assert.assertTrue("Dashboard must not render while signed out", nodes.isEmpty())
        }
    }
}
