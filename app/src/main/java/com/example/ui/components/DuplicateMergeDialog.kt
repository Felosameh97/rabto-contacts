package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.MergeType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ContactRecord

@Composable
fun DuplicateMergeDialog(
    clusters: List<List<ContactRecord>>,
    onMergeCluster: (List<ContactRecord>) -> Unit,
    onMergeAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.MergeType, contentDescription = null) },
        title = {
            Text(
                text = if (clusters.isEmpty()) {
                    "لا توجد جهات مكررة"
                } else {
                    "توحيد ودمج المكررات (${clusters.size})"
                }
            )
        },
        text = {
            if (clusters.isEmpty()) {
                Text(
                    text = "قاعدة بياناتك نظيفة! لم يتم رصد أي جهات اتصال متطابقة في الرقم أو البريد أو الاسم.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(clusters) { idx, cluster ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dup_cluster_$idx"),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = "${cluster.size} سجلات متطابقة",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    FilledTonalButton(
                                        onClick = { onMergeCluster(cluster) },
                                        modifier = Modifier.testTag("dup_merge_single_$idx"),
                                    ) {
                                        Icon(Icons.Rounded.MergeType, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("دمج")
                                    }
                                }
                                cluster.forEach { c ->
                                    val phone = c.primaryPhone.ifBlank { c.phones.firstOrNull().orEmpty() }
                                    Text(
                                        text = "• ${c.displayName.ifBlank { phone }} — $phone",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (clusters.isNotEmpty()) {
                Button(
                    onClick = onMergeAll,
                    modifier = Modifier.testTag("dup_merge_all_btn"),
                ) {
                    Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("دمج الكل (${clusters.size})")
                }
            } else {
                Button(onClick = onDismiss) { Text("تم") }
            }
        },
        dismissButton = {
            if (clusters.isNotEmpty()) {
                TextButton(onClick = onDismiss) { Text("إغلاق") }
            }
        },
    )
}
