package io.github.tatuya911.snaptomd

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.tatuya911.snaptomd.ui.theme.SnapToMDTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_NOTE_ID = "EXTRA_NOTE_ID"
    }

    private var targetNoteId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 起動インテントのハンドリング
        handleIncomingIntent(intent)

        setContent {
            SnapToMDTheme {
                // Android 13+ の通知権限リクエスト
                RequestNotificationPermission()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(initialNoteId = targetNoteId)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * 共有インテントまたは通知タップによるインテントを処理する
     */
    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        // 1. 通知タップによる特定ノートの表示
        if (intent.hasExtra(EXTRA_NOTE_ID)) {
            val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
            if (noteId != -1L) {
                targetNoteId = noteId
            }
        }
        // 画像共有 (ACTION_SEND) は ShareReceiverActivity が受け取る
    }
}

@Composable
fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun AppNavigation(initialNoteId: Long? = null) {
    val navController = rememberNavController()

    // 通知タップから遷移してきた場合の自動遷移
    LaunchedEffect(initialNoteId) {
        if (initialNoteId != null && initialNoteId != -1L) {
            navController.navigate("detail/$initialNoteId")
        }
    }

    NavHost(navController = navController, startDestination = "history") {
        composable("history") {
            HistoryScreen(
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToDetail = { noteId -> navController.navigate("detail/$noteId") }
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = "detail/{noteId}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId") ?: 0L
            DetailScreen(
                noteId = noteId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
