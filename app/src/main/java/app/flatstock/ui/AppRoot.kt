package app.flatstock.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.flatstock.FlatStockApp
import app.flatstock.data.Repository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface Session {
    data object Loading : Session
    data object NotConfigured : Session
    data object SignedOut : Session
    data class Unverified(val email: String) : Session
    data class NoWg(val email: String) : Session
    data class Ready(val uid: String, val email: String, val wgId: String, val groupIds: List<String>) : Session
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel(app: Application) : AndroidViewModel(app) {
    private val maybeRepo: Repository? = (app as FlatStockApp).repository
    val repo: Repository get() = maybeRepo!!
    private val refresh = MutableStateFlow(0)

    val session: StateFlow<Session> = if (maybeRepo == null) {
        MutableStateFlow(Session.NotConfigured)
    } else {
        combine(maybeRepo.authState(), refresh) { _, _ -> maybeRepo.auth.currentUser }
            .flatMapLatest { user ->
                when {
                    user == null -> flowOf<Session>(Session.SignedOut)
                    !user.isEmailVerified -> flowOf<Session>(Session.Unverified(user.email.orEmpty()))
                    else -> maybeRepo.userGroupsFlow(user.uid).map { groups ->
                        val active = groups.activeId
                        if (active.isNullOrBlank()) Session.NoWg(user.email.orEmpty())
                        else Session.Ready(user.uid, user.email.orEmpty(), active, groups.groupIds)
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, Session.Loading)
    }

    /** Call after the user clicked the link in the verification email. */
    suspend fun checkVerified(): Boolean = repo.reloadUser().also { refresh.value++ }
}

@Composable
fun AppRoot(vm: SessionViewModel = viewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = session) {
            Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            Session.NotConfigured -> NotConfiguredScreen()
            Session.SignedOut -> AuthScreen(vm.repo)
            is Session.Unverified -> VerifyEmailScreen(s.email, vm)
            is Session.NoWg -> WgSetupScreen(s.email, vm.repo)
            is Session.Ready -> MainScreen(s, vm.repo)
        }
    }
}
