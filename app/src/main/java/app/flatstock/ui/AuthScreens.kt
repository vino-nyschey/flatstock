package app.flatstock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flatstock.R
import app.flatstock.data.Repository

@Composable
private fun CenteredPage(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** Shown by builds made without app/google-services.json. */
@Composable
fun NotConfiguredScreen() {
    CenteredPage {
        Icon(Icons.Default.Settings, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.not_configured_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.not_configured_body), textAlign = TextAlign.Center)
    }
}

@Composable
fun AuthScreen(repo: Repository) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var registering by rememberSaveable { mutableStateOf(false) }
    val runner = rememberActionRunner()
    val resetSent = stringResource(R.string.reset_sent)
    val enterEmailFirst = stringResource(R.string.enter_email_first)

    CenteredPage {
        Icon(Icons.Default.Home, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (registering) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.your_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                supportingText = { Text(stringResource(R.string.your_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            supportingText = { if (registering) Text(stringResource(R.string.password_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        runner.message?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
        Button(
            onClick = { runner.run { if (registering) repo.register(name, email, password) else repo.signIn(email, password) } },
            enabled = !runner.busy && email.isNotBlank() && password.length >= 6 && (!registering || name.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (runner.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(if (registering) R.string.create_account else R.string.sign_in))
        }
        if (registering) {
            Text(
                stringResource(R.string.privacy_body),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { registering = !registering }) {
            Text(stringResource(if (registering) R.string.have_account else R.string.no_account))
        }
        if (!registering) {
            TextButton(onClick = {
                if (email.isBlank()) runner.message = enterEmailFirst
                else runner.run { repo.resetPassword(email); runner.message = resetSent }
            }) { Text(stringResource(R.string.forgot_password)) }
        }
        LanguagePicker()
    }
}

@Composable
fun VerifyEmailScreen(email: String, vm: SessionViewModel) {
    val runner = rememberActionRunner()
    val notYet = stringResource(R.string.verify_not_yet)
    val resent = stringResource(R.string.verify_resent)

    CenteredPage {
        Icon(Icons.Default.MarkEmailUnread, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.verify_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.verify_body, email), textAlign = TextAlign.Center)
        runner.message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, textAlign = TextAlign.Center) }
        Button(
            onClick = { runner.run { if (!vm.checkVerified()) runner.message = notYet } },
            enabled = !runner.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.verify_done)) }
        OutlinedButton(
            onClick = { runner.run { vm.repo.resendVerification(); runner.message = resent } },
            enabled = !runner.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.verify_resend)) }
        TextButton(onClick = { vm.repo.signOut() }) { Text(stringResource(R.string.sign_out)) }
    }
}

@Composable
fun WgSetupScreen(email: String, repo: Repository) {
    val invites by remember(email) { repo.pendingInvites(email) }.collectAsStateWithLifecycle(emptyList())
    var wgName by rememberSaveable { mutableStateOf("") }
    val starter = stringArrayResource(R.array.starter_supplies).toList()
    val runner = rememberActionRunner()

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)

        if (invites.isNotEmpty()) {
            Text(stringResource(R.string.invites_for_you), style = MaterialTheme.typography.titleMedium)
            invites.forEach { invite ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(invite.wgName, style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.invite_from, invite.invitedBy), style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { runner.run { repo.declineInvite(invite) } }, enabled = !runner.busy) {
                            Text(stringResource(R.string.decline))
                        }
                        Button(onClick = { runner.run { repo.acceptInvite(invite) } }, enabled = !runner.busy) {
                            Text(stringResource(R.string.join))
                        }
                    }
                }
            }
            HorizontalDivider()
        }

        Text(stringResource(R.string.create_wg_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = wgName,
            onValueChange = { wgName = it },
            label = { Text(stringResource(R.string.wg_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.create_wg_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { runner.run { repo.createWg(wgName, starter) } },
            enabled = wgName.isNotBlank() && !runner.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.create)) }
        runner.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            stringResource(R.string.setup_hint, email),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { repo.signOut() }) { Text(stringResource(R.string.sign_out)) }
    }
}
