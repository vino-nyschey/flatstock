package app.flatstock.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.flatstock.R
import app.flatstock.data.Repository
import app.flatstock.data.Supply
import app.flatstock.data.Wg
import app.flatstock.domain.Level
import app.flatstock.domain.parseCents
import app.flatstock.domain.splitEqually
import kotlinx.coroutines.launch

@Composable
fun SuppliesScreen(wg: Wg, supplies: List<Supply>, me: String, repo: Repository, snackbar: SnackbarHostState) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var buying by remember { mutableStateOf<Supply?>(null) }
    var editing by remember { mutableStateOf<Supply?>(null) }
    val scope = rememberCoroutineScope()
    val savedText = stringResource(R.string.purchase_saved)
    val groups = Level.entries.reversed().associateWith { level -> supplies.filter { it.levelEnum == level } }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (supplies.isEmpty()) {
                item { Text(stringResource(R.string.supplies_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            groups.forEach { (level, list) ->
                if (list.isEmpty()) return@forEach
                item(key = "h_$level") {
                    Text(
                        stringResource(
                            when (level) {
                                Level.OUT -> R.string.section_out
                                Level.LOW -> R.string.section_low
                                Level.FULL -> R.string.section_full
                            },
                            list.size,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = levelColor(level),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(list, key = { it.id }) { supply ->
                    SupplyRow(
                        wg, supply,
                        onLevel = { repo.setLevel(wg.id, supply.id, it) },
                        onCount = { repo.changeCount(wg.id, supply, it) },
                        onBought = { buying = supply },
                        onEdit = { editing = supply },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Default.Add, null) },
            text = { Text(stringResource(R.string.add_supply)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (adding) {
        NameDialog(
            title = stringResource(R.string.add_supply),
            initial = "",
            onConfirm = { repo.addSupply(wg.id, it); adding = false },
            onDismiss = { adding = false },
        )
    }
    editing?.let { supply ->
        NameDialog(
            title = stringResource(R.string.edit_supply),
            initial = supply.name,
            onConfirm = { repo.renameSupply(wg.id, supply.id, it); editing = null },
            onDismiss = { editing = null },
            onDelete = { repo.deleteSupply(wg.id, supply.id); editing = null },
        )
    }
    buying?.let { supply ->
        PurchaseDialog(wg, supply, me, repo, onDismiss = { buying = null }, onSaved = {
            buying = null
            scope.launch { snackbar.showSnackbar(savedText) }
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplyRow(wg: Wg, supply: Supply, onLevel: (Level) -> Unit, onCount: (Long) -> Unit, onBought: () -> Unit, onEdit: () -> Unit) {
    val level = supply.levelEnum
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (level == Level.FULL) MaterialTheme.colorScheme.surfaceContainer
            else levelColor(level).copy(alpha = 0.10f),
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
                    Text(supply.name, style = MaterialTheme.typography.titleMedium)
                    if (level != Level.FULL && supply.updatedBy.isNotBlank()) {
                        Text(
                            stringResource(R.string.marked_by, wg.nameOf(supply.updatedBy), formatDate(supply.updatedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(onClick = onBought) {
                    Icon(Icons.Default.ShoppingCart, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.bought))
                }
            }
            PackCounter(supply.count, onCount)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Level.entries.forEachIndexed { i, l ->
                    SegmentedButton(
                        selected = level == l,
                        onClick = { if (level != l) onLevel(l) },
                        shape = SegmentedButtonDefaults.itemShape(i, Level.entries.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = levelColor(l).copy(alpha = 0.22f),
                            activeContentColor = levelColor(l),
                        ),
                    ) { Text(levelLabel(l)) }
                }
            }
        }
    }
}

/** "−  3 packs  +": − when one pack is used up, + for a pack found in the cupboard. */
@Composable
fun PackCounter(count: Long, onCount: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onCount(-1) }, enabled = count > 0) {
            Icon(Icons.Default.Remove, stringResource(R.string.used_one))
        }
        Text(
            pluralStringResource(R.plurals.packs_in_stock, count.toInt(), count.toInt()),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(120.dp),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = { onCount(1) }) { Icon(Icons.Default.Add, stringResource(R.string.add_pack)) }
    }
}

@Composable
fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * "I bought it": optional cost, who paid (default me) and who shares it (default everyone).
 * With a [supply] it is also marked full again; without one the user types what was bought.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PurchaseDialog(wg: Wg, supply: Supply?, me: String, repo: Repository, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var itemName by rememberSaveable { mutableStateOf(supply?.name.orEmpty()) }
    var cost by rememberSaveable { mutableStateOf("") }
    var packsText by rememberSaveable { mutableStateOf("1") }
    val packs = packsText.toLongOrNull()?.takeIf { it in 1..999 }
    var paidBy by rememberSaveable { mutableStateOf(if (me in wg.members) me else wg.members.first()) }
    var sharedBy by remember { mutableStateOf(wg.members.toSet()) }
    val runner = rememberActionRunner()
    val cents = parseCents(cost)
    val costValid = cost.isBlank() || cents != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (supply != null) stringResource(R.string.bought_title, supply.name) else stringResource(R.string.add_purchase)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (supply == null) {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text(stringResource(R.string.what_bought)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                if (supply != null) {
                    OutlinedTextField(
                        value = packsText,
                        onValueChange = { packsText = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.packs_bought)) },
                        singleLine = true,
                        isError = packs == null,
                        supportingText = { Text(stringResource(R.string.packs_bought_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = cost,
                    onValueChange = { cost = it },
                    label = { Text(stringResource(R.string.cost_optional)) },
                    singleLine = true,
                    isError = !costValid,
                    prefix = { Text("€ ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (cents != null) {
                    Text(stringResource(R.string.paid_by), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        wg.members.forEach { uid ->
                            FilterChip(selected = paidBy == uid, onClick = { paidBy = uid }, label = { Text(wg.nameOf(uid)) })
                        }
                    }
                    Text(stringResource(R.string.shared_by), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        wg.members.forEach { uid ->
                            val on = uid in sharedBy
                            FilterChip(
                                selected = on,
                                onClick = { sharedBy = if (on) sharedBy - uid else sharedBy + uid },
                                label = { Text(wg.nameOf(uid)) },
                                leadingIcon = if (on) ({ Icon(Icons.Default.Check, null) }) else null,
                            )
                        }
                    }
                    val shares = splitEqually(cents, sharedBy)
                    Text(
                        if (shares.isEmpty()) stringResource(R.string.pick_someone)
                        else stringResource(R.string.each_pays, formatCents(shares.values.min()), sharedBy.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (shares.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(R.string.no_cost_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                runner.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runner.run {
                        repo.recordPurchase(
                            wg.id, supply, itemName, if (supply != null) packs ?: 1L else 0L, cents ?: 0L,
                            paidBy = if (cents != null) paidBy else me,
                            sharedBy = if (cents != null) sharedBy.toList() else wg.members,
                        )
                        onSaved()
                    }
                },
                enabled = !runner.busy && costValid && itemName.isNotBlank() && (cents == null || sharedBy.isNotEmpty()) &&
                    (supply == null || packs != null),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
