package com.feedbackthread.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.feedbackthread.sdk.FeedbackThreadClient
import com.feedbackthread.sdk.FeedbackThreadBoard
import com.feedbackthread.sdk.FeedbackThreadFeedbackScreen

/** One client for the whole app; screens are stateless and just borrow it. */
private val client = FeedbackThreadClient(ExampleConfig.configuration)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FeedbackThreadExampleApp()
                }
            }
        }
    }
}

@Composable
private fun FeedbackThreadExampleApp() {
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Request a feature") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Feature requests") })
        }

        val screenModifier = Modifier.weight(1f)
        if (tab == 0) {
            FeedbackThreadFeedbackScreen(
                client = client,
                onDismiss = {},
                modifier = screenModifier,
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            )
        } else {
            FeedbackThreadBoard(
                client = client,
                onDismiss = {},
                onAddRequest = { tab = 0 },
                modifier = screenModifier,
            )
        }
    }
}
