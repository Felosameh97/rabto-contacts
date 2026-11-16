package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.CategorySummary
import com.example.domain.model.ContactRecord
import com.example.ui.components.ContactSummaryCard
import com.example.util.SystemIntents

@Composable
fun CategoriesScreen(
    currentPath: String,
    childCategories: List<CategorySummary>,
    contactsInCategory: List<ContactRecord>,
    selectedBulkIds: Set<String>,
    onSelectPath: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onCreateCategory: (String) -> Unit,
    onOpenContact: (ContactRecord) -> Unit,
    onWhatsApp: (ContactRecord, String) -> Unit,
    onAddContactInCategory: (String) -> Unit,
    onToggleBulk: (String) -> Unit,
    onClearBulk: () -> Unit,
    onShareExport: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var bulkMode by remember { mutableStateOf(false) }
    var showNewCatDialog by remember { mutableStateOf(false) }

    if (currentPath.isNotEmpty()) {
        BackHandler {
            onNavigateUp()
        }
    }

    val segments = remember(currentPath) {
        if (currentPath.isBlank()) emptyList() else currentPath.split(" > ")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header & Breadcrumb bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentPath.isNotEmpty()) {
                            IconButton(onClick = onNavigateUp, modifier = Modifier.testTag("cat_back_btn")) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع للفئة السابقة")
                            }
                        }
                        Text(
                            text = if (currentPath.isEmpty()) "الفئات المتداخلة" else segments.last(),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { showNewCatDialog = true }, modifier = Modifier.testTag("cat_new_btn")) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "فئة جديدة")
                        }
                        if (contactsInCategory.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    bulkMode = !bulkMode
                                    if (!bulkMode) onClearBulk()
                                },
                                modifier = Modifier.testTag("cat_bulk_toggle_btn"),
                            ) {
                                Icon(
                                    if (bulkMode) Icons.Rounded.Close else Icons.Rounded.Checklist,
                                    contentDescription = "تحديد متعدد",
                                )
                            }
                        }
                    }
                }

                // Breadcrumbs
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AssistChip(
                        onClick = { onSelectPath("") },
                        label = { Text("الكل") },
                    )
                    var accum = ""
                    segments.forEach { seg ->
                        accum = if (accum.isEmpty()) seg else "$accum > $seg"
                        val target = accum
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        AssistChip(
                            onClick = { onSelectPath(target) },
                            label = { Text(seg) },
                        )
                    }
                }

                if (bulkMode && selectedBulkIds.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { onShareExport(true) },
                            modifier = Modifier.weight(1f).testTag("bulk_share_vcf_btn"),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مشاركة (${selectedBulkIds.size}) vCard")
                        }
                        OutlinedButton(
                            onClick = { onShareExport(false) },
                            modifier = Modifier.weight(1f).testTag("bulk_share_csv_btn"),
                        ) {
                            Text("CSV")
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (currentPath.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { onAddContactInCategory(currentPath) },
                            modifier = Modifier.weight(1f).testTag("cat_add_contact_btn"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("إضافة رقم هنا")
                        }
                        if (contactsInCategory.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { onShareExport(true) },
                                modifier = Modifier.testTag("cat_share_all_btn"),
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("مشاركة الفئة")
                            }
                        }
                    }
                }
            }

            if (childCategories.isNotEmpty()) {
                item {
                    Text(
                        text = if (currentPath.isEmpty()) "الأقسام الرئيسية (${childCategories.size})" else "فئات فرعية (${childCategories.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(childCategories, key = { it.fullPath }) { cat ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPath(cat.fullPath) }
                            .testTag("category_folder_${cat.name}"),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(cat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                val sub = buildList {
                                    add("${cat.contactCount} رقم")
                                    if (cat.childCount > 0) add("${cat.childCount} فئة فرعية")
                                }.joinToString(" • ")
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }

            if (contactsInCategory.isNotEmpty()) {
                item {
                    Text(
                        text = "جهات الاتصال (${contactsInCategory.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(contactsInCategory, key = { it.id }) { contact ->
                    ContactSummaryCard(
                        contact = contact,
                        onClick = { onOpenContact(contact) },
                        onCall = { phone -> SystemIntents.dialPhone(context, phone) },
                        onWhatsApp = { phone -> onWhatsApp(contact, phone) },
                        bulkMode = bulkMode,
                        isSelected = selectedBulkIds.contains(contact.id),
                        onToggleSelect = { onToggleBulk(contact.id) },
                    )
                }
            }

            if (childCategories.isEmpty() && contactsInCategory.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                            Text("لا توجد فئات أو جهات اتصال هنا بعد", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "أنشئ فئة متداخلة (مثل: الجامعة > هندسة، العيلة، المطرية، أمين شرطة) أو استورد ملفات CSV.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewCatDialog) {
        var newName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCatDialog = false },
            title = { Text("إنشاء فئة جديدة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentPath.isNotEmpty()) {
                        Text("سيتم الإنشاء داخل: $currentPath", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("يمكنك كتابة مسار متداخل باستخدام > مثل:\nالجامعة > هندسة > دفعة 2024", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("اسم الفئة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("new_category_input"),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            val full = if (currentPath.isEmpty()) newName.trim() else "$currentPath > ${newName.trim()}"
                            onCreateCategory(full)
                            showNewCatDialog = false
                        }
                    },
                    modifier = Modifier.testTag("new_category_save_btn"),
                ) { Text("إنشاء") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCatDialog = false }) { Text("إلغاء") }
            },
        )
    }
}
