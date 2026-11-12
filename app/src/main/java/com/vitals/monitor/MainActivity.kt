package com.vitals.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vitals.monitor.data.Sampler
import com.vitals.monitor.data.Snapshot
import com.vitals.monitor.ui.AppsScreen
import com.vitals.monitor.ui.History
import com.vitals.monitor.ui.NetworkScreen
import com.vitals.monitor.ui.OverviewScreen
import com.vitals.monitor.ui.SensorsScreen
import com.vitals.monitor.ui.SystemScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme(colorScheme = dynamicDarkColorScheme(LocalContext.current)) {
                VitalsApp()
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Overview", Icons.Filled.Home),
    Tab("Network", Icons.Filled.Share),
    Tab("Apps", Icons.Filled.List),
    Tab("Sensors", Icons.Filled.LocationOn),
    Tab("System", Icons.Filled.Info),
)

@Composable
fun VitalsApp() {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val sampler = remember { Sampler(ctx) }
    val history = remember { History() }
    var snap by remember { mutableStateOf<Snapshot?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        // Only poll while the app is on screen; the HUD service has its own loop.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val s = withContext(Dispatchers.IO) { sampler.sample() }
                history.add(s)
                snap = s
                delay(1000)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .fillMaxSize(),
        ) {
            val s = snap
            if (s == null && tab != 2 && tab != 3) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                when (tab) {
                    0 -> OverviewScreen(s!!, history)
                    1 -> NetworkScreen(s!!, history)
                    2 -> AppsScreen()
                    3 -> SensorsScreen()
                    else -> SystemScreen(s!!)
                }
            }
        }
    }
}
