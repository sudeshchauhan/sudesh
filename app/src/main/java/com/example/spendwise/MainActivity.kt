package com.example.spendwise

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(s: Bundle?) { super.onCreate(s); setContent { MaterialTheme { App() } } }
}

private fun money(v: Double) = "₹" + "%,.0f".format(v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()
    val txns by dao.txns().collectAsState(emptyList())
    val budgets by dao.budgets().collectAsState(emptyList())
    val cats by dao.cats().collectAsState(emptyList())
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }
    val monthStart = remember {
        Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    }
    val month = txns.filter { it.date >= monthStart }
    val spent = month.filter { it.isExpense }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = { NavigationBar {
            listOf("Spends", "Budgets", "Settings").forEachIndexed { i, l ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(l) }) } } },
        floatingActionButton = { if (tab == 0) FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "Add") } }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Column(Modifier.padding(16.dp)) {
                    Text("This month", style = MaterialTheme.typography.labelLarge)
                    Text("Spent ${money(spent.values.sum())}   Income ${money(month.filter { !it.isExpense }.sumOf { it.amount })}",
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn { items(txns, key = { it.id }) { t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.note.ifBlank { t.category }, style = MaterialTheme.typography.bodyLarge)
                                Text("${t.category} • " + SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(t.date)) +
                                    if (t.smsKey != null) " • SMS" else "", style = MaterialTheme.typography.bodySmall)
                            }
                            Text((if (t.isExpense) "-" else "+") + money(t.amount),
                                color = if (t.isExpense) Color(0xFFC62828) else Color(0xFF2E7D32))
                            IconButton({ scope.launch { dao.delete(t) } }) { Icon(Icons.Default.Delete, "Delete") }
                        }
                        HorizontalDivider()
                    } }
                }
                1 -> LazyColumn(Modifier.padding(16.dp)) { items(cats.filter { it.name != "Income" }) { c ->
                    val limit = budgets.firstOrNull { it.category == c.name }?.monthlyLimit ?: 0.0
                    val s = spent[c.name] ?: 0.0
                    var edit by remember { mutableStateOf(false) }
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row { Text(c.name, Modifier.weight(1f)); TextButton({ edit = true }) { Text(if (limit > 0) "${money(s)} / ${money(limit)}" else "${money(s)} • set budget") } }
                        if (limit > 0) LinearProgressIndicator(progress = { (s / limit).toFloat().coerceIn(0f, 1f) },
                            color = if (s > limit) Color(0xFFC62828) else MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth())
                    }
                    if (edit) {
                        var v by remember { mutableStateOf(if (limit > 0) limit.toLong().toString() else "") }
                        AlertDialog(onDismissRequest = { edit = false }, title = { Text("${c.name} monthly budget") },
                            text = { OutlinedTextField(v, { v = it }, singleLine = true, label = { Text("Amount") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
                            confirmButton = { TextButton({ scope.launch { dao.setBudget(Budget(c.name, v.toDoubleOrNull() ?: 0.0)) }; edit = false }) { Text("Save") } },
                            dismissButton = { TextButton({ edit = false }) { Text("Cancel") } })
                    }
                } }
                else -> SettingsTab(dao, snack)
            }
        }
    }
    if (showAdd) AddDialog(cats, { showAdd = false }) { t -> scope.launch { dao.insert(t) }; showAdd = false }
}

@Composable
fun SettingsTab(dao: AppDao, snack: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var newCat by remember { mutableStateOf("") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) scope.launch { snack.showSnackbar("Imported ${SmsParser.scanInbox(ctx)} transactions from SMS") }
        else scope.launch { snack.showSnackbar("SMS permission denied") }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        u?.let { scope.launch { runCatching { Backup.export(ctx, it) }.onSuccess { snack.showSnackbar("Backup saved") }.onFailure { snack.showSnackbar("Backup failed") } } } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { scope.launch { runCatching { Backup.restore(ctx, it) }.onSuccess { snack.showSnackbar("Restored $it transactions") }.onFailure { snack.showSnackbar("Invalid backup file") } } } }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SMS", style = MaterialTheme.typography.titleMedium)
        Button({ perm.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }) { Text("Enable auto-read & scan last 60 days") }
        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ save.launch("spendwise-backup.json") }) { Text("Backup") }
            OutlinedButton({ open.launch(arrayOf("application/json", "*/*")) }) { Text("Restore") }
        }
        Text("Restore replaces all current data.", style = MaterialTheme.typography.bodySmall)
        Text("Categories", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(newCat, { newCat = it }, label = { Text("New category") }, singleLine = true, modifier = Modifier.weight(1f))
            Button({ if (newCat.isNotBlank()) { scope.launch { dao.addCat(Category(newCat.trim())) }; newCat = "" } }) { Text("Add") }
        }
    }
}

@Composable
fun AddDialog(cats: List<Category>, onDismiss: () -> Unit, onSave: (Txn) -> Unit) {
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf(true) }
    var cat by remember { mutableStateOf(cats.firstOrNull()?.name ?: "Other") }
    var open by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Add transaction") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(exp, { exp = true }, { Text("Expense") }); FilterChip(!exp, { exp = false }, { Text("Income") }) }
            OutlinedTextField(amt, { amt = it }, label = { Text("Amount") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true)
            Box { OutlinedButton({ open = true }) { Text(cat) }
                DropdownMenu(open, { open = false }) { cats.forEach { c -> DropdownMenuItem({ Text(c.name) }, { cat = c.name; open = false }) } } }
        } },
        confirmButton = { TextButton({ amt.toDoubleOrNull()?.let { onSave(Txn(amount = it, isExpense = exp, category = if (exp) cat else "Income", note = note)) } }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}
