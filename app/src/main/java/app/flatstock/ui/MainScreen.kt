package app.flatstock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flatstock.R
import app.flatstock.data.Repository
import app.flatstock.data.Supply
import app.flatstock.work.Reminders

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(session: Session.Ready, repo: Repository) {
    val wid = session.wgId
    val wg by remember(wid) { repo.wgFlow(wid) }.collectAsStateWithLifecycle(null)
    val supplies by remember(wid) { repo.suppliesFlow(wid) }.collectAsStateWithLifecycle(emptyList<Supply>())
    val purchases by remember(wid) { repo.purchasesFlow(wid) }.collectAsStateWithLifecycle(emptyList())
    val payments by remember(wid) { repo.paymentsFlow(wid) }.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // While the app runs, flatmates' "low"/"out" marks notify at once instead of at the next background check.
    LaunchedEffect(wg, supplies) {
        val current = wg ?: return@LaunchedEffect
        if (supplies.isNotEmpty()) Reminders.checkAlerts(context, current, supplies, session.uid)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(wg?.name ?: stringResource(R.string.app_name)) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text(stringResource(R.string.tab_supplies)) },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Euro, null) }, label = { Text(stringResource(R.string.tab_money)) },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Group, null) }, label = { Text(stringResource(R.string.tab_wg)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = wg
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (current == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else when (tab) {
                0 -> SuppliesScreen(current, supplies, session.uid, repo, snackbar)
                1 -> MoneyScreen(current, purchases, payments, session.uid, repo, snackbar)
                else -> WgScreen(current, session, repo)
            }
        }
    }
}
