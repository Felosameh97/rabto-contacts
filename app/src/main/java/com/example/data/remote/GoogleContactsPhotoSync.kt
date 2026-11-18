package com.example.data.remote

import android.app.Activity
import android.content.Context
import com.example.data.local.ContactEntity
import com.example.domain.model.ContactRecord
import com.example.util.ContactPhotoStorage
import com.example.util.PhoneUtils
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
import java.util.concurrent.TimeUnit

data class GooglePhotoSyncResult(
    val scannedConnections: Int,
    val photosDownloaded: Int,
    val matchedContactsCount: Int,
    val photoUpdatesByContactId: Map<String, String>,
)

/**
 * Strictly one-way Google Contacts photo pull via Google People API (`contacts.readonly`).
 * Saves downloaded photos locally inside app-private storage (`filesDir/contact_photos/`)
 * without writing anything back to Google Contacts or Android's ContactsContract.
 */
object GoogleContactsPhotoSync {
    const val CONTACTS_READONLY_SCOPE = "https://www.googleapis.com/auth/contacts.readonly"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun requestContactsAuthorization(activity: Activity): Result<AuthorizationResult> {
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(CONTACTS_READONLY_SCOPE)))
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

    suspend fun fetchAndCachePhotos(
        context: Context,
        accessToken: String,
        existingContacts: List<ContactRecord>,
    ): Result<GooglePhotoSyncResult> = withContext(Dispatchers.IO) {
        try {
            val byPhoneKey = HashMap<String, MutableList<ContactRecord>>()
            val byEmail = HashMap<String, MutableList<ContactRecord>>()
            val byNormName = HashMap<String, MutableList<ContactRecord>>()

            existingContacts.forEach { contact ->
                val allPhones = (listOf(contact.primaryPhone) + contact.phones).filter { it.isNotBlank() }
                allPhones.forEach { p ->
                    val key = PhoneUtils.phoneSearchKey(p)
                    if (key.length >= 6) {
                        byPhoneKey.getOrPut(key) { mutableListOf() }.add(contact)
                    }
                }
                contact.emails.forEach { e ->
                    val cleanEmail = e.trim().lowercase()
                    if (cleanEmail.contains("@")) {
                        byEmail.getOrPut(cleanEmail) { mutableListOf() }.add(contact)
                    }
                }
                val normName = ContactEntity.normalizeForSearch(contact.displayName).trim()
                if (normName.isNotBlank()) {
                    byNormName.getOrPut(normName) { mutableListOf() }.add(contact)
                }
            }

            var scanned = 0
            var downloaded = 0
            val updates = LinkedHashMap<String, String>()
            var pageToken: String? = null
            var pages = 0

            do {
                pages++
                val url = buildString {
                    append("https://people.googleapis.com/v1/people/me/connections")
                    append("?personFields=names,phoneNumbers,emailAddresses,photos&pageSize=1000")
                    if (!pageToken.isNullOrBlank()) {
                        append("&pageToken=").append(pageToken)
                    }
                }
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                val bodyStr = client.newCall(req).execute().use { resp ->
                    val txt = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException("تعذر سحب صور Google Contacts (${resp.code}): ${txt.take(180)}")
                    }
                    txt
                }

                val root = JSONObject(bodyStr)
                val connections = root.optJSONArray("connections")
                if (connections != null) {
                    for (i in 0 until connections.length()) {
                        val person = connections.optJSONObject(i) ?: continue
                        scanned++

                        val photosArr = person.optJSONArray("photos") ?: continue
                        var chosenPhotoUrl: String? = null
                        for (pIdx in 0 until photosArr.length()) {
                            val pObj = photosArr.optJSONObject(pIdx) ?: continue
                            val meta = pObj.optJSONObject("metadata")
                            if (meta?.optBoolean("default", false) == true) continue
                            val pUrl = pObj.optString("url").takeIf { it.isNotBlank() } ?: continue
                            chosenPhotoUrl = pUrl
                            if (meta?.optBoolean("primary", false) == true) break
                        }
                        if (chosenPhotoUrl == null) continue

                        val matchedTargets = LinkedHashSet<ContactRecord>()
                        val phonesArr = person.optJSONArray("phoneNumbers")
                        if (phonesArr != null) {
                            for (k in 0 until phonesArr.length()) {
                                val rawVal = phonesArr.optJSONObject(k)?.optString("value").orEmpty()
                                val key = PhoneUtils.phoneSearchKey(rawVal)
                                if (key.length >= 6) {
                                    byPhoneKey[key]?.let { matchedTargets.addAll(it) }
                                }
                            }
                        }

                        if (matchedTargets.isEmpty()) {
                            val emailsArr = person.optJSONArray("emailAddresses")
                            if (emailsArr != null) {
                                for (k in 0 until emailsArr.length()) {
                                    val em = emailsArr.optJSONObject(k)?.optString("value").orEmpty().trim().lowercase()
                                    if (em.isNotBlank()) {
                                        byEmail[em]?.let { matchedTargets.addAll(it) }
                                    }
                                }
                            }
                        }

                        if (matchedTargets.isEmpty()) {
                            val namesArr = person.optJSONArray("names")
                            val gName = namesArr?.optJSONObject(0)?.optString("displayName").orEmpty()
                            val normGName = ContactEntity.normalizeForSearch(gName).trim()
                            if (normGName.length >= 3) {
                                byNormName[normGName]?.let { matchedTargets.addAll(it) }
                            }
                        }

                        if (matchedTargets.isEmpty()) continue

                        val firstTarget = matchedTargets.first()
                        val localPath = ContactPhotoStorage.savePhotoFromUrl(
                            context = context,
                            rawUrl = chosenPhotoUrl,
                            contactIdHint = "g_${firstTarget.id}",
                            bearerToken = null, // People API photo URLs are public CDN signed URLs
                        ) ?: ContactPhotoStorage.savePhotoFromUrl(
                            context = context,
                            rawUrl = chosenPhotoUrl,
                            contactIdHint = "g_${firstTarget.id}",
                            bearerToken = accessToken,
                        )
                        if (localPath != null) {
                            downloaded++
                            matchedTargets.forEach { target ->
                                updates[target.id] = localPath
                            }
                        }
                    }
                }

                pageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
            } while (pageToken != null && pages < 10)

            Result.success(
                GooglePhotoSyncResult(
                    scannedConnections = scanned,
                    photosDownloaded = downloaded,
                    matchedContactsCount = updates.size,
                    photoUpdatesByContactId = updates,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
