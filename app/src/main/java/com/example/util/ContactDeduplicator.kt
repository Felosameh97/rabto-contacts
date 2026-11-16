package com.example.util

import com.example.domain.model.ContactRecord
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object ContactDeduplicator {

    fun deterministicContactId(
        phones: List<String>,
        emails: List<String>,
        fallbackName: String = "",
    ): String {
        val phoneKey = phones.asSequence()
            .map { PhoneUtils.phoneSearchKey(it) }
            .firstOrNull { it.length >= 7 }
        if (!phoneKey.isNullOrBlank()) {
            return "c_ph_$phoneKey"
        }
        val emailKey = emails.asSequence()
            .map { it.trim().lowercase(Locale.ROOT) }
            .firstOrNull { it.contains("@") }
        val seed = emailKey ?: fallbackName.trim().lowercase(Locale.ROOT)
        if (seed.isBlank()) {
            return "c_" + UUID.randomUUID().toString().replace("-", "").take(16)
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        val hex = bytes.joinToString("") { "%02x".format(it) }.take(16)
        return "c_h_$hex"
    }

    data class DeduplicationResult(
        val mergedContacts: List<ContactRecord>,
        val supersededIds: List<String>,
        val duplicateGroupsMerged: Int,
        val totalDuplicatesRemoved: Int,
    )

    /**
     * Enriches a single contact with English primary `displayName`, Arabic `nickname`,
     * normalized nested `categoryPaths`, and searchable bilingual `tags`.
     */
    fun enrichSingleContact(record: ContactRecord, enforceBilingual: Boolean = true): ContactRecord {
        val normPhones = (listOf(record.primaryPhone) + record.phones)
            .map { PhoneUtils.normalizePhone(it) }
            .filter { it.isNotBlank() }
            .distinctBy { PhoneUtils.phoneSearchKey(it).ifBlank { it } }
            .take(10)

        val normEmails = record.emails
            .map { it.trim() }
            .filter { it.contains("@") }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(5)

        val nameVariants = buildList {
            if (record.displayName.isNotBlank()) add(record.displayName)
            record.nickname?.takeIf { it.isNotBlank() }?.let { add(it) }
        }

        val (finalName, finalNickname) = if (enforceBilingual) {
            BilingualNameEngine.resolveEnglishAndNickname(nameVariants)
        } else {
            record.displayName to record.nickname
        }

        val (nestedCats, isStarred) = SmartCategoryEngine.normalizeAndNestLabels(
            rawLabels = record.categoryPaths,
            displayName = listOfNotNull(finalName, finalNickname).joinToString(" "),
            phones = normPhones,
            emails = normEmails,
            organization = record.organization,
            jobTitle = record.jobTitle,
            notes = record.notes,
            address = record.address,
        )

        val geo = if (record.latitude == null || record.longitude == null) {
            EgyptGeoLookup.inferLocation(
                record.address,
                record.district,
                finalName,
                finalNickname,
                record.notes,
                nestedCats.joinToString(" "),
            ) ?: normPhones.firstOrNull()?.let { EgyptGeoLookup.inferLocationFromPhone(it) }
        } else null

        val district = record.district ?: geo?.key
        val tags = SmartCategoryEngine.generateTags(
            displayName = finalName,
            nickname = finalNickname,
            phones = normPhones,
            emails = normEmails,
            categoryPaths = nestedCats,
            organization = record.organization,
            jobTitle = record.jobTitle,
            notes = record.notes,
            address = record.address,
            district = district,
            existingTags = record.tags,
        )

        return record.copy(
            id = record.id.ifBlank { deterministicContactId(normPhones, normEmails, finalName) },
            displayName = finalName.ifBlank { normPhones.firstOrNull().orEmpty() }.ifBlank { normEmails.firstOrNull().orEmpty() },
            nickname = finalNickname,
            primaryPhone = normPhones.firstOrNull().orEmpty(),
            phones = normPhones,
            emails = normEmails,
            categoryPaths = nestedCats,
            tags = tags,
            district = district,
            latitude = record.latitude ?: geo?.lat,
            longitude = record.longitude ?: geo?.lng,
            isFavorite = record.isFavorite || isStarred,
        )
    }

    /**
     * Transitive Union-Find deduplication across ALL phone keys and emails.
     * Merges any contacts sharing a phone number or email into ONE richly-detailed bilingual record
     * preserving BOTH short & long name variants, English primary + Arabic nickname, all phones/emails,
     * merged notes, categories, and searchable tags.
     */
    fun deduplicateAndMerge(
        records: List<ContactRecord>,
        enforceBilingual: Boolean = true,
    ): DeduplicationResult {
        if (records.isEmpty()) {
            return DeduplicationResult(emptyList(), emptyList(), 0, 0)
        }
        val n = records.size
        val parent = IntArray(n) { it }

        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) {
                parent[r] = parent[parent[r]]
                r = parent[r]
            }
            return r
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[rb] = ra
        }

        val firstOwnerByPhoneKey = HashMap<String, Int>(n * 2)
        val firstOwnerByEmail = HashMap<String, Int>(n)

        for (i in records.indices) {
            val rec = records[i]
            val phones = (listOf(rec.primaryPhone) + rec.phones).filter { it.isNotBlank() }
            for (p in phones) {
                val key = PhoneUtils.phoneSearchKey(p)
                if (key.length >= 7) {
                    val prev = firstOwnerByPhoneKey[key]
                    if (prev == null) {
                        firstOwnerByPhoneKey[key] = i
                    } else {
                        union(prev, i)
                    }
                }
            }
            for (em in rec.emails) {
                val ek = em.trim().lowercase(Locale.ROOT)
                if (ek.contains("@")) {
                    val prev = firstOwnerByEmail[ek]
                    if (prev == null) {
                        firstOwnerByEmail[ek] = i
                    } else {
                        union(prev, i)
                    }
                }
            }
        }

        val clusters = LinkedHashMap<Int, MutableList<ContactRecord>>()
        for (i in records.indices) {
            clusters.getOrPut(find(i)) { ArrayList(2) }.add(records[i])
        }

        val mergedList = ArrayList<ContactRecord>(clusters.size)
        val supersededIds = ArrayList<String>()
        var dupGroups = 0
        var totalRemoved = 0

        for (cluster in clusters.values) {
            if (cluster.size == 1) {
                mergedList += enrichSingleContact(cluster.first(), enforceBilingual)
            } else {
                dupGroups++
                totalRemoved += (cluster.size - 1)
                val (winner, droppedIds) = mergeCluster(cluster, enforceBilingual)
                mergedList += winner
                supersededIds += droppedIds
            }
        }

        return DeduplicationResult(
            mergedContacts = mergedList,
            supersededIds = supersededIds.distinct(),
            duplicateGroupsMerged = dupGroups,
            totalDuplicatesRemoved = totalRemoved,
        )
    }

    private fun mergeCluster(
        cluster: List<ContactRecord>,
        enforceBilingual: Boolean,
    ): Pair<ContactRecord, List<String>> {
        // Choose canonical winner ID: prefer syncedToDevice, persisted non-blank id, favorite, richest detail
        val sorted = cluster.sortedWith(
            compareByDescending<ContactRecord> { it.id.isNotBlank() && !it.id.startsWith("tmp_") }
                .thenByDescending { it.syncedToDevice }
                .thenByDescending { it.isFavorite }
                .thenByDescending { richnessScore(it) }
        )
        val anchor = sorted.first()
        val winnerId = anchor.id.ifBlank { "c_" + UUID.randomUUID().toString().replace("-", "").take(16) }
        val droppedIds = cluster.map { it.id }.filter { it.isNotBlank() && it != winnerId }

        val allNameVariants = LinkedHashSet<String>()
        val allPhones = LinkedHashMap<String, String>()
        val allEmails = LinkedHashMap<String, String>()
        val allRawCategories = LinkedHashSet<String>()
        val allCustomTags = LinkedHashSet<String>()
        val allNotesLines = LinkedHashSet<String>()

        var bestOrg: String? = null
        var bestJobTitle: String? = null
        var bestAddress: String? = null
        var bestDistrict: String? = null
        var bestLat: Double? = null
        var bestLng: Double? = null
        var bestTelegram: String? = null
        var bestWebsite: String? = null
        var bestPhotoUri: String? = null
        var anySynced = false
        var anyFavorite = false

        for (item in sorted) {
            if (item.displayName.isNotBlank()) allNameVariants += item.displayName
            item.nickname?.takeIf { it.isNotBlank() }?.let { allNameVariants += it }

            for (rawP in listOf(item.primaryPhone) + item.phones) {
                val norm = PhoneUtils.normalizePhone(rawP)
                if (norm.isBlank()) continue
                val key = PhoneUtils.phoneSearchKey(norm).ifBlank { norm }
                val existing = allPhones[key]
                if (existing == null || (norm.startsWith("+") && !existing.startsWith("+"))) {
                    allPhones[key] = norm
                }
            }

            for (rawE in item.emails) {
                val trim = rawE.trim()
                if (trim.contains("@")) {
                    allEmails.putIfAbsent(trim.lowercase(Locale.ROOT), trim)
                }
            }

            item.categoryPaths.filter { it.isNotBlank() }.forEach { allRawCategories += it }
            item.tags.filter { it.isNotBlank() }.forEach { allCustomTags += it }

            item.notes?.lines()?.map { it.trim() }?.filter { it.isNotBlank() }?.forEach { allNotesLines += it }

            if (bestOrg.isNullOrBlank() || (item.organization?.length ?: 0) > (bestOrg?.length ?: 0)) {
                item.organization?.takeIf { it.isNotBlank() }?.let { bestOrg = it }
            }
            if (bestJobTitle.isNullOrBlank() || (item.jobTitle?.length ?: 0) > (bestJobTitle?.length ?: 0)) {
                item.jobTitle?.takeIf { it.isNotBlank() }?.let { bestJobTitle = it }
            }
            if (bestAddress.isNullOrBlank() || (item.address?.length ?: 0) > (bestAddress?.length ?: 0)) {
                item.address?.takeIf { it.isNotBlank() }?.let { bestAddress = it }
            }
            if (bestDistrict.isNullOrBlank()) {
                item.district?.takeIf { it.isNotBlank() }?.let { bestDistrict = it }
            }
            if (bestLat == null && item.latitude != null && item.longitude != null) {
                bestLat = item.latitude
                bestLng = item.longitude
            }
            if (bestTelegram.isNullOrBlank()) {
                item.telegramHandle?.takeIf { it.isNotBlank() }?.let { bestTelegram = it }
            }
            if (bestWebsite.isNullOrBlank()) {
                item.website?.takeIf { it.isNotBlank() }?.let { bestWebsite = it }
            }
            if (bestPhotoUri.isNullOrBlank()) {
                item.photoUri?.takeIf { it.isNotBlank() }?.let { bestPhotoUri = it }
            }
            if (item.syncedToDevice) anySynced = true
            if (item.isFavorite) anyFavorite = true
        }

        val mergedPhones = allPhones.values.take(10)
        val mergedEmails = allEmails.values.take(5)
        val mergedNotes = allNotesLines.joinToString("\n").take(3800).ifBlank { null }

        val (finalName, finalNickname) = if (enforceBilingual) {
            BilingualNameEngine.resolveEnglishAndNickname(allNameVariants.toList())
        } else {
            val bestName = allNameVariants.maxByOrNull { it.length }.orEmpty()
            val rest = allNameVariants.filter { it != bestName }.joinToString(" • ").ifBlank { null }
            bestName to rest
        }

        val (nestedCats, starredFromCat) = SmartCategoryEngine.normalizeAndNestLabels(
            rawLabels = allRawCategories.toList(),
            displayName = listOfNotNull(finalName, finalNickname).joinToString(" "),
            phones = mergedPhones,
            emails = mergedEmails,
            organization = bestOrg,
            jobTitle = bestJobTitle,
            notes = mergedNotes,
            address = bestAddress,
        )

        val geo = if (bestLat == null || bestLng == null) {
            EgyptGeoLookup.inferLocation(bestAddress, bestDistrict, finalName, finalNickname, mergedNotes, nestedCats.joinToString(" "))
                ?: mergedPhones.firstOrNull()?.let { EgyptGeoLookup.inferLocationFromPhone(it) }
        } else null

        val finalDistrict = bestDistrict ?: geo?.key
        val mergedTags = SmartCategoryEngine.generateTags(
            displayName = finalName,
            nickname = finalNickname,
            phones = mergedPhones,
            emails = mergedEmails,
            categoryPaths = nestedCats,
            organization = bestOrg,
            jobTitle = bestJobTitle,
            notes = mergedNotes,
            address = bestAddress,
            district = finalDistrict,
            existingTags = allCustomTags.toList(),
        )

        val winner = ContactRecord(
            id = winnerId,
            displayName = finalName.ifBlank { mergedPhones.firstOrNull().orEmpty() }.ifBlank { mergedEmails.firstOrNull().orEmpty() },
            nickname = finalNickname,
            primaryPhone = mergedPhones.firstOrNull().orEmpty(),
            phones = mergedPhones,
            emails = mergedEmails,
            categoryPaths = nestedCats,
            tags = mergedTags,
            organization = bestOrg,
            jobTitle = bestJobTitle,
            notes = mergedNotes,
            address = bestAddress,
            district = finalDistrict,
            latitude = bestLat ?: geo?.lat,
            longitude = bestLng ?: geo?.lng,
            telegramHandle = bestTelegram,
            website = bestWebsite,
            photoUri = bestPhotoUri,
            syncedToDevice = anySynced,
            isFavorite = anyFavorite || starredFromCat,
            createdAt = anchor.createdAt,
        )
        return winner to droppedIds
    }

    private fun richnessScore(c: ContactRecord): Int {
        var score = c.displayName.length
        score += (c.nickname?.length ?: 0)
        score += c.phones.size * 25
        score += c.emails.size * 20
        score += c.categoryPaths.size * 15
        score += c.tags.size * 10
        if (!c.photoUri.isNullOrBlank()) score += 30
        if (!c.organization.isNullOrBlank()) score += 20
        if (!c.address.isNullOrBlank()) score += 20
        if (!c.notes.isNullOrBlank()) score += 15
        return score
    }
}
