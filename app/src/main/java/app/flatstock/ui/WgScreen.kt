package app.flatstock.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.flatstock.R
import app.flatstock.data.Repository
import app.flatstock.data.Wg
import app.flatstock.work.Reminders
import app.flatstock.work.SummaryMode

@Composable
fun WgScreen(wg: Wg, session: Session.Ready, repo: Repository) {
    val context = LocalContext.current
    val sent by remember(wg.id) { repo.sentInvites(wg.id) }.collectAsStateWithLifecycle(emptyList())
    var inviteEmail by rememberSaveable { mutableStateOf("") }
    var renamingMe by rememberSaveable { mutableStateOf(false) }
    var renamingWg by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val runner = rememberActionRunner()
    val noMailApp = stringResource(R.string.invite_no_mail_app)
    val inviteSaved = stringResource(R.string.invite_saved)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(stringResource(R.string.members)) {
            wg.members.forEach { uid ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(wg.nameOf(uid) + if (uid == session.uid) " " + stringResource(R.string.you) else "")
                        Text(
                            wg.memberEmails[uid].orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (uid == session.uid) {
                        IconButton(onClick = { renamingMe = true }) { Icon(Icons.Default.Edit, stringResource(R.string.change_name)) }
                    }
                }
            }
            TextButton(onClick = { renamingWg = true }) { Text(stringResource(R.string.rename_wg)) }
        }

        SectionCard(stringResource(R.string.invite_title)) {
            Text(stringResource(R.string.invite_body_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = inviteEmail,
                onValueChange = { inviteEmail = it },
                label = { Text(stringResource(R.string.email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val email = inviteEmail.trim().lowercase()
                    runner.run {
                        repo.createInvite(wg, email)
                        inviteEmail = ""
                        runner.message = if (sendInviteEmail(context, email, wg.name, wg.nameOf(session.uid))) inviteSaved else noMailApp
                    }
                },
                enabled = EMAIL_RE.matches(inviteEmail.trim()) && !runner.busy,
            ) { Text(stringResource(R.string.invite_send)) }
            runner.message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            if (sent.isNotEmpty()) {
                Text(stringResource(R.string.invites_pending), style = MaterialTheme.typography.labelLarge)
                sent.forEach { invite ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(invite.email, Modifier.weight(1f))
                        IconButton(onClick = { runner.run { repo.cancelInvite(invite) } }) {
                            Icon(Icons.Default.Close, stringResource(R.string.cancel))
                        }
                    }
                }
            }
        }

        SectionCard(stringResource(R.string.reminders)) {
            Text(stringResource(R.string.reminders_hint), style = MaterialTheme.typography.bodySmall)
            var mode by remember { mutableStateOf(Reminders.mode(context)) }
            listOf(
                SummaryMode.DAILY to R.string.summary_daily,
                SummaryMode.WEEKLY to R.string.summary_weekly,
                SummaryMode.OFF to R.string.summary_off,
            ).forEach { (m, label) ->
                Row(
                    Modifier.fillMaxWidth().clickable { mode = m; Reminders.setMode(context, m) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = mode == m, onClick = null, modifier = Modifier.padding(12.dp))
                    Text(stringResource(label))
                }
            }
        }

        SectionCard(stringResource(R.string.language)) { LanguagePicker() }

        OutlinedButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.leave_wg), color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = { Reminders.reset(context); repo.signOut() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sign_out))
        }
        Text(
            stringResource(R.string.signed_in_as, session.email) + " · v" + appVersion(context),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (renamingMe) {
        NameDialog(
            title = stringResource(R.string.change_name),
            initial = wg.memberNames[session.uid].orEmpty(),
            onConfirm = { name -> runner.run { repo.setMyName(wg.id, name) }; renamingMe = false },
            onDismiss = { renamingMe = false },
        )
    }
    if (renamingWg) {
        NameDialog(
            title = stringResource(R.string.rename_wg),
            initial = wg.name,
            onConfirm = { name -> runner.run { repo.renameWg(wg.id, name) }; renamingWg = false },
            onDismiss = { renamingWg = false },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.leave_wg)) },
            text = { Text(stringResource(R.string.leave_wg_body, wg.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    Reminders.reset(context)
                    runner.run { repo.leaveWg(wg.id, session.groupIds) }
                }) { Text(stringResource(R.string.leave_wg), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun LanguagePicker() {
    val current = remember { AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',').substringBefore('-') }
    val options = listOf("" to stringResource(R.string.lang_system), "en" to "English", "de" to "Deutsch")
    Column {
        options.forEach { (tag, label) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        // The activity restarts in the new language.
                        AppCompatDelegate.setApplicationLocales(
                            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = current == tag, onClick = null, modifier = Modifier.padding(12.dp))
                Text(label)
            }
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

/** Opens the phone's email app with the invitation filled in. Returns false if there is no email app. */
private fun sendInviteEmail(context: Context, to: String, wgName: String, from: String): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.invite_subject, wgName))
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.invite_mail_body, wgName, from, to, context.getString(R.string.web_url)))
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
