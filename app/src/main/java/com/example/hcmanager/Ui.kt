package com.example.hcmanager

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun ConfigStatus.color() = when (this) {
    ConfigStatus.WORKING -> Color(0xFF2E7D32)
    ConfigStatus.OLD -> Color(0xFFEF6C00)
    ConfigStatus.UNTESTED -> Color(0xFF757575)
}

private fun formatDate(ms: Long) =
    SimpleDateFormat("yyyy/MM/dd - HH:mm", Locale.US).format(Date(ms))

private fun formatSize(b: Long) =
    if (b < 1024) "$b B" else String.format(Locale.US, "%.1f KB", b / 1024.0)

@Composable
fun App(vm: ConfigViewModel = viewModel()) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val id = selectedId
    if (id == null) {
        ListScreen(vm) { selectedId = it }
    } else {
        BackHandler { selectedId = null }
        DetailScreen(vm, id) { selectedId = null }
    }
}

@Composable
fun StatusBadge(status: ConfigStatus) {
    Surface(
        shape = RoundedCornerShape(50),
        color = status.color().copy(alpha = 0.15f)
    ) {
        Text(
            status.label,
            color = status.color(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: ConfigViewModel, onOpen: (Long) -> Unit) {
    val ctx = LocalContext.current
    val items by vm.configs.collectAsState()
    val query by vm.query.collectAsState()
    val filter by vm.filter.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri) { msg ->
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("مدير الكونفجات") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("استيراد .hc") }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("بحث بالاسم أو الملاحظة") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Default.Close, "مسح")
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == null,
                    onClick = { vm.filter.value = null },
                    label = { Text("الكل") }
                )
                ConfigStatus.entries.forEach { s ->
                    FilterChip(
                        selected = filter == s,
                        onClick = { vm.filter.value = if (filter == s) null else s },
                        label = { Text(s.label) }
                    )
                }
            }

            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "مفيش كونفجات لسه.\nاضغط \"استيراد .hc\" عشان تضيف ملف.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { c ->
                        Card(onClick = { onOpen(c.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        c.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    StatusBadge(ConfigStatus.from(c.status))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    formatDate(c.addedAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (c.note.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(c.note, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: ConfigViewModel, id: Long, onBack: () -> Unit) {
    val c by vm.observe(id).collectAsState(initial = null)
    val config = c ?: return
    var note by remember(config.id) { mutableStateOf(config.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التفاصيل") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "حذف") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(config.name, style = MaterialTheme.typography.headlineSmall)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("تاريخ الإضافة", formatDate(config.addedAt))
                    InfoRow("الحجم", formatSize(config.sizeBytes))
                }
            }

            Text("الحالة", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConfigStatus.entries.forEach { s ->
                    FilterChip(
                        selected = config.status == s.name,
                        onClick = { vm.setStatus(config.id, s) },
                        label = { Text(s.label) }
                    )
                }
            }

            Text("ملاحظة", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("اكتب ملاحظتك هنا...") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    vm.setNote(config.id, note.trim())
                    Toast.makeText(ctx, "تم حفظ الملاحظة", Toast.LENGTH_SHORT).show()
                },
                enabled = note.trim() != config.note,
                modifier = Modifier.fillMaxWidth()
            ) { Text("حفظ الملاحظة") }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف الكونفج؟") },
            text = { Text("هيتم حذف \"${config.name}\" نهائيًا من التطبيق.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(config)
                    onBack()
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}
