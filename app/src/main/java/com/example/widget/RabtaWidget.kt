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
import com.example.domain.model.ContactRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RabtaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RabtaWidget()
}

class RabtaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val quickContacts = withContext(Dispatchers.IO) {
            val dao = RabtaDatabase.getInstance(context).contactDao()
            val favs = dao.getFavoritesOnce(4).map { it.toDomain() }
            if (favs.isNotEmpty()) favs else dao.getRecentOrAllPage(limit = 4, offset = 0).map { it.toDomain() }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context = context, contacts = quickContacts)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context, contacts: List<ContactRecord>) {
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
                .padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "رابطة السريعة",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Row(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable(actionStartActivity(dialIntent)),
                ) {
                    Text(
                        text = "لوحة الاتصال",
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

            if (contacts.isEmpty()) {
                Text(
                    text = "افتح رابطة لاستيراد جهات الاتصال وتثبيت المفضلة",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    contacts.take(4).forEachIndexed { idx, contact ->
                        if (idx > 0) Spacer(GlanceModifier.width(6.dp))
                        val phone = contact.primaryPhone.ifBlank { contact.phones.firstOrNull().orEmpty() }
                        val callIntent = if (phone.isNotBlank()) {
                            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
                        } else {
                            Intent(context, MainActivity::class.java)
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
                                text = contact.displayName.take(10),
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
