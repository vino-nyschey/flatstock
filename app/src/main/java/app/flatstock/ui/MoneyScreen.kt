package app.flatstock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.flatstock.R
import app.flatstock.data.Purchase
import app.flatstock.data.Repository
import app.flatstock.data.SettlePayment
import app.flatstock.data.Wg
import app.flatstock.domain.Transfer
import app.flatstock.domain.balances
import app.flatstock.domain.parseCents
import app.flatstock.domain.settle
import app.flatstock.ui.theme.LevelColors
import kotlinx.coroutines.launch

private const val HISTORY_LIMIT = 50

@Composable
fun MoneyScreen(
    wg: Wg,
    purchases: List<Purchase>,
    payments: List<SettlePayment>,
    me: String,
    repo: Repository,
    snackbar: SnackbarHostState,
) {
    val net = remember(purchases, payments) { balances(purchases.map { it.toExpense() }, payments.map { it.toPayment() }) }
    val transfers = remember(net) { settle(net) }
    // Current members always show; people who left only while they still owe or are owed.
    val people = (wg.members + net.filterValues { it != 0L }.keys).distinct()
    var settling by remember { mutableStateOf<Transfer?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Any?>(null) }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.purchase_saved)
    val paymentText = stringResource(R.string.payment_saved)

    val history: List<Any> = (purchases + payments)
        .sortedByDescending { if (it is Purchase) it.createdAt else (it as SettlePayment).createdAt }
        .take(HISTORY_LIMIT)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            val mine = net[me] ?: 0L
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.your_balance), style = MaterialTheme.typography.labelLarge)
                    Text(
                        when {
                            mine > 0 -> stringResource(R.string.you_are_owed, formatCents(mine))
                            mine < 0 -> stringResource(R.string.you_owe, formatCents(-mine))
                            else -> stringResource(R.string.all_settled)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Text(stringResource(R.string.add_purchase))
            }
        }
        item {
            SectionCard(stringResource(R.string.who_owes_whom)) {
                if (transfers.isEmpty()) {
                    Text(stringResource(R.string.nobody_owes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                transfers.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.owes_line, wg.nameOf(t.from), wg.nameOf(t.to), formatCents(t.amountCents)),
                            modifier = Modifier.weight(1f),
                            fontWeight = if (t.from == me || t.to == me) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Button(onClick = { settling = t }) { Text(stringResource(R.string.settle_up)) }
                    }
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.balances)) {
                people.forEach { uid ->
                    val v = net[uid] ?: 0L
                    Row {
                        Text(
                            wg.nameOf(uid) + if (uid !in wg.members) " " + stringResource(R.string.left_wg) else "",
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            (if (v > 0) "+" else "") + formatCents(v),
                            color = when {
                                v > 0 -> LevelColors.full
                                v < 0 -> LevelColors.out
                                else -> LevelColors.neutral
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        item { Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium) }
        if (history.isEmpty()) {
            item { Text(stringResource(R.string.history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(history, key = { if (it is Purchase) "p_" + it.id else "s_" + (it as SettlePayment).id }) { entry ->
            HistoryRow(wg, entry, me, onDelete = { deleting = entry })
            HorizontalDivider()
        }
    }

    if (adding) {
        PurchaseDialog(wg, null, me, repo, onDismiss = { adding = false }, onSaved = {
            adding = false
            scope.launch { snackbar.showSnackbar(savedText) }
        })
    }
    settling?.let { t ->
        SettleDialog(wg, t, repo, onDismiss = { settling = null }, onSaved = {
            settling = null
            scope.launch { snackbar.showSnackbar(paymentText) }
        })
    }
    deleting?.let { entry ->
        val runner = rememberActionRunner()
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_entry_title)) },
            text = { Text(runner.message ?: stringResource(R.string.delete_entry_body)) },
            confirmButton = {
                TextButton(onClick = {
                    runner.run {
                        if (entry is Purchase) repo.deletePurchase(wg.id, entry.id)
                        else repo.deletePayment(wg.id, (entry as SettlePayment).id)
                        deleting = null
                    }
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun HistoryRow(wg: Wg, entry: Any, me: String, onDelete: () -> Unit) {
    val (title, detail, createdBy) = when (entry) {
        is Purchase -> Triple(
            if (entry.amountCents > 0) stringResource(R.string.history_purchase, wg.nameOf(entry.paidBy), entry.itemName, formatCents(entry.amountCents))
            else stringResource(R.string.history_purchase_no_cost, wg.nameOf(entry.paidBy), entry.itemName),
            formatDate(entry.createdAt) + if (entry.amountCents > 0) " · " + stringResource(
                R.string.history_shared, entry.sharedBy.joinToString(", ") { wg.nameOf(it) },
            ) else "",
            entry.createdBy,
        )
        is SettlePayment -> Triple(
            stringResource(R.string.history_payment, wg.nameOf(entry.fromUid), wg.nameOf(entry.toUid), formatCents(entry.amountCents)),
            formatDate(entry.createdAt),
            entry.createdBy,
        )
        else -> return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (createdBy == me) {
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
        }
    }
}

@Composable
private fun SettleDialog(wg: Wg, transfer: Transfer, repo: Repository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf(centsInput(transfer.amountCents)) }
    val cents = parseCents(amount)
    val runner = rememberActionRunner()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settle_up)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settle_body, wg.nameOf(transfer.from), wg.nameOf(transfer.to)))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.amount)) },
                    singleLine = true,
                    isError = cents == null,
                    prefix = { Text("€ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                runner.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { runner.run { repo.recordPayment(wg.id, transfer.from, transfer.to, cents!!); onSaved() } },
                enabled = cents != null && !runner.busy,
            ) { Text(stringResource(R.string.record_payment)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
