package com.example.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.data.local.RabtaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RabtaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RabtaWidget()
}

data class WidgetCallerSlot(
    val title: String,
    val phone: String,
)

class RabtaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quickCallers = withContext(Dispatchers.IO) {
            val db = RabtaDatabase.getInstance(context)
            val recentDialed = db.dialHistoryDao().getActiveOnce(4)
                .filter { it.phoneNumber.isNotBlank() }
                .map {
                    WidgetCallerSlot(
                        title = it.matchedName ?: it.phoneNumber,
                        phone = it.phoneNumber,
                    )
                }
            if (recentDialed.isNotEmpty()) {
                recentDialed
            } else {
                val dao = db.contactDao()
                val favs = dao.getFavoritesOnce(4).ifEmpty {
                    dao.getRecentOrAllPage(limit = 4, offset = 0)
                }
                favs.map { entity ->
                    val phone = entity.primaryPhone
                    WidgetCallerSlot(
                        title = entity.displayName,
                        phone = phone,
                    )
                }
            }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context = context, callers = quickCallers)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, callers: List<WidgetCallerSlot>) {
        val dialIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DIALPAD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val aiIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_AI
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(20.dp)
                .padding(12.dp)
                .clickable(actionStartActivity(dialIntent)),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Felo Contacts • الكيباد",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier
                        .defaultWeight()
                        .clickable(actionStartActivity(dialIntent)),
                )
                Row(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable(actionStartActivity(dialIntent)),
                ) {
                    Text(
                        text = "فتح الكيباد",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
                Spacer(GlanceModifier.width(6.dp))
                Row(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.secondaryContainer)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable(actionStartActivity(aiIntent)),
                ) {
                    Text(
                        text = "Gemini",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }

            Spacer(GlanceModifier.height(10.dp))

            if (callers.isEmpty()) {
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .background(GlanceTheme.colors.surfaceVariant)
                        .cornerRadius(14.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clickable(actionStartActivity(dialIntent)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "اضغط هنا لفتح الكيباد والاتصال الفوري من Felo Contacts",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                    )
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    callers.take(4).forEachIndexed { idx, slot ->
                        if (idx > 0) Spacer(GlanceModifier.width(6.dp))
                        val phone = slot.phone
                        val callIntent = if (phone.isNotBlank()) {
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
                        } else {
                            dialIntent
                        }
                        Column(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .background(GlanceTheme.colors.surfaceVariant)
                                .cornerRadius(14.dp)
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                                .clickable(actionStartActivity(callIntent)),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = slot.title.take(10),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                maxLines = 1,
                            )
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = phone.takeLast(11).ifBlank { "اتصال" },
                                style = TextStyle(
                                    color = GlanceTheme.colors.primary,
                                    fontSize = 10.sp,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_DIALPAD = "com.example.widget.ACTION_OPEN_DIALPAD"
        const val ACTION_OPEN_AI = "com.example.widget.ACTION_OPEN_AI"

        suspend fun refreshAll(context: Context) {
            runCatching { RabtaWidget().updateAll(context.applicationContext) }
        }
    }
}
