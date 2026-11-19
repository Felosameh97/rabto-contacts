package com.example.data.remote

import android.app.Activity
import com.example.domain.model.ContactRecord
import com.example.util.CsvParser
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveScanOutcome(
    val filesScanned: Int,
    val scannedTitles: List<String>,
    val contacts: List<ContactRecord>,
    val categoryPaths: Set<String>,
)

object GoogleDriveScanner {
    const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun requestDriveAuthorization(activity: Activity): Result<AuthorizationResult> {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_READONLY_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(activity)
                .authorize(request)
                .await()
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Searches the user's Google Drive for Spreadsheets, CSVs, Text notes, and VCF files,
     * exports/downloads them using the granted OAuth access token, and extracts every contact name + mobile number.
     */
    suspend fun scanAndExtractContacts(accessToken: String): Result<DriveScanOutcome> = withContext(Dispatchers.IO) {
        try {
            val query = "trashed = false and (" +
                "mimeType = 'application/vnd.google-apps.spreadsheet' or " +
                "mimeType = 'text/csv' or " +
                "mimeType = 'text/plain' or " +
                "mimeType = 'text/x-vcard' or " +
                "name contains '.csv' or " +
                "name contains '.vcf'" +
                ")"
            val encodedQ = URLEncoder.encode(query, "UTF-8")
            val listUrl = "https://www.googleapis.com/drive/v3/files" +
                "?q=$encodedQ&pageSize=25&orderBy=modifiedTime%20desc" +
                "&fields=files(id,name,mimeType,size)"

            val listReq = Request.Builder()
                .url(listUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            val listBody = client.newCall(listReq).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException("تعذر الوصول إلى Google Drive (${resp.code}): ${bodyStr.take(180)}")
                }
                bodyStr
            }

            val root = JSONObject(listBody)
            val filesArr = root.optJSONArray("files")
            if (filesArr == null || filesArr.length() == 0) {
                return@withContext Result.success(
                    DriveScanOutcome(0, emptyList(), emptyList(), emptySet())
                )
            }

            val allContacts = ArrayList<ContactRecord>()
            val allCategories = LinkedHashSet<String>()
            val scannedTitles = ArrayList<String>()

            for (i in 0 until filesArr.length()) {
                val f = filesArr.optJSONObject(i) ?: continue
                val fileId = f.optString("id").takeIf { it.isNotBlank() } ?: continue
                val fileName = f.optString("name", "Drive File")
                val mimeType = f.optString("mimeType", "")
                val sizeBytes = f.optString("size").toLongOrNull() ?: 0L
                if (sizeBytes > 30_000_000L) continue

                val downloadUrl = if (mimeType == "application/vnd.google-apps.spreadsheet") {
                    "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=text%2Fcsv"
                } else {
                    "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
                }

                val parsed = runCatching {
                    val req = Request.Builder()
                        .url(downloadUrl)
                        .addHeader("Authorization", "Bearer $accessToken")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val stream = resp.body?.byteStream() ?: return@use null
                        val title = fileName.substringBeforeLast('.')
                        CsvParser.parseStreamAuto(
                            inputStream = stream,
                            sourceTitle = title,
                            defaultCategory = "Google Drive > $title",
                            skipEnrich = true,
                        )
                    }
                }.getOrNull()

                if (parsed != null && parsed.contacts.isNotEmpty()) {
                    scannedTitles += "$fileName (${parsed.contacts.size})"
                    allContacts += parsed.contacts
                    allCategories += parsed.categoryPaths
                }
            }

            Result.success(
                DriveScanOutcome(
                    filesScanned = scannedTitles.size,
                    scannedTitles = scannedTitles,
                    contacts = allContacts,
                    categoryPaths = allCategories,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
