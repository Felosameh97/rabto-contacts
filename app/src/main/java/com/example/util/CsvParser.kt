package com.example.util

import com.example.domain.model.ContactRecord
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

object CsvParser {

    data class ParsedDataset(
        val contacts: List<ContactRecord>,
        val skippedCount: Int,
        val categoryPaths: Set<String>,
    )

    fun parseStream(inputStream: InputStream, defaultCategory: String? = null): ParsedDataset {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val rows = readAllCsvRows(reader)
        if (rows.isEmpty()) return ParsedDataset(emptyList(), 0, emptySet())

        val headers = rows.first().map { it.trim().removePrefix("\uFEFF") }
        val headerIndex = headers.withIndex().associate { (i, name) -> name.lowercase() to i }

        val isGoogleContacts = headerIndex.containsKey("first name") ||
            headerIndex.containsKey("phone 1 - value") ||
            headerIndex.containsKey("labels")

        return if (isGoogleContacts) {
            parseGoogleContactsRows(headers, headerIndex, rows.drop(1), defaultCategory)
        } else {
            parseGenericRows(headers, rows.drop(1), defaultCategory)
        }
    }

    /**
     * Parses arbitrary Drive spreadsheets, CSVs, TSVs, or text dumps: falls back to scanning
     * every row for valid mobile numbers + nearest text name/email if standard headers are absent.
     */
    fun parseArbitrarySheetOrText(rawText: String, sourceTitle: String): ParsedDataset {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return ParsedDataset(emptyList(), 0, emptySet())

        val vcards = if (trimmed.contains("BEGIN:VCARD", ignoreCase = true)) {
            VCardUtils.parseVCards(trimmed)
        } else emptyList()
        if (vcards.isNotEmpty()) {
            val cats = LinkedHashSet<String>()
            val tagged = vcards.map { c ->
                val cat = "Google Drive > $sourceTitle"
                addHierarchySegments(cat, cats)
                c.copy(categoryPaths = (c.categoryPaths + cat).distinct())
            }
            return ParsedDataset(tagged, 0, cats)
        }

        // Try standard CSV parsing first
        val csvAttempt = trimmed.byteInputStream(StandardCharsets.UTF_8).use {
            parseStream(it, defaultCategory = "Google Drive > $sourceTitle")
        }
        if (csvAttempt.contacts.isNotEmpty()) return csvAttempt

        // Fallback: line-by-line regex extraction for messy sheets/notes/TSV
        val contacts = ArrayList<ContactRecord>()
        val categories = LinkedHashSet<String>()
        val driveCat = "Google Drive > $sourceTitle"
        addHierarchySegments(driveCat, categories)
        val phoneRegex = Regex("""(\+?\d[\d\s\-().]{7,17}\d)""")

        for (line in trimmed.lineSequence()) {
            val cleanLine = line.trim()
            if (cleanLine.isBlank()) continue
            val matches = phoneRegex.findAll(cleanLine)
                .map { PhoneUtils.normalizePhone(it.value) }
                .filter { it.length >= 9 }
                .distinct()
                .toList()
            if (matches.isEmpty()) continue

            val textWithoutPhones = phoneRegex.replace(cleanLine, " ")
                .replace(Regex("[,;\t|]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            val emailMatch = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""").find(textWithoutPhones)?.value
            val name = textWithoutPhones
                .replace(emailMatch.orEmpty(), "")
                .trim(' ', '-', ':', '•')
                .ifBlank { matches.first() }
                .take(160)

            val (smartCats, starred) = SmartCategoryEngine.normalizeAndNestLabels(
                rawLabels = listOf(driveCat),
                displayName = name,
                phones = matches,
                emails = listOfNotNull(emailMatch),
                organization = null,
                jobTitle = null,
                notes = null,
                address = null,
            )
            smartCats.forEach { addHierarchySegments(it, categories) }
            val geo = EgyptGeoLookup.inferLocation(name, sourceTitle) ?: EgyptGeoLookup.inferLocationFromPhone(matches.first())

            contacts += ContactDeduplicator.enrichSingleContact(
                ContactRecord(
                    id = ContactDeduplicator.deterministicContactId(matches, listOfNotNull(emailMatch), name),
                    displayName = name,
                    primaryPhone = matches.first(),
                    phones = matches,
                    emails = listOfNotNull(emailMatch),
                    categoryPaths = smartCats,
                    district = geo?.key,
                    latitude = geo?.lat,
                    longitude = geo?.lng,
                    isFavorite = starred,
                )
            )
        }
        return ParsedDataset(contacts, 0, categories)
    }

    private fun parseGoogleContactsRows(
        headers: List<String>,
        headerIndex: Map<String, Int>,
        dataRows: List<List<String>>,
        defaultCategory: String?,
    ): ParsedDataset {
        val phoneIndices = headers.withIndex()
            .filter { it.value.startsWith("Phone", ignoreCase = true) && it.value.endsWith("Value", ignoreCase = true) }
            .map { it.index }
        val emailIndices = headers.withIndex()
            .filter { it.value.startsWith("E-mail", ignoreCase = true) && it.value.endsWith("Value", ignoreCase = true) }
            .map { it.index }
        val addressIndices = headers.withIndex()
            .filter {
                it.value.startsWith("Address", ignoreCase = true) &&
                    (it.value.endsWith("Formatted", ignoreCase = true) ||
                        it.value.endsWith("Street", ignoreCase = true) ||
                        it.value.endsWith("City", ignoreCase = true) ||
                        it.value.endsWith("Region", ignoreCase = true))
            }
            .map { it.index }

        val contacts = ArrayList<ContactRecord>(dataRows.size)
        val categories = LinkedHashSet<String>()
        var skipped = 0

        for (row in dataRows) {
            fun col(name: String): String = headerIndex[name.lowercase()]?.let { row.getOrNull(it)?.trim() }.orEmpty()

            val phones = phoneIndices
                .flatMap { idx -> splitGoogleMulti(row.getOrNull(idx)) }
                .map { PhoneUtils.normalizePhone(it) }
                .filter { it.isNotBlank() && it != "'0" && it != "0" }
                .distinct()
                .take(10)

            val emails = emailIndices
                .flatMap { idx -> splitGoogleMulti(row.getOrNull(idx)) }
                .filter { it.contains("@") }
                .distinct()
                .take(5)

            val first = col("First Name")
            val middle = col("Middle Name")
            val last = col("Last Name")
            val nickname = col("Nickname")
            val fileAs = col("File As")
            val org = col("Organization Name")
            val title = col("Organization Title")
            val notes = col("Notes")
            val website = splitGoogleMulti(col("Website 1 - Value")).firstOrNull()

            val rawName = listOf(first, middle, last)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { nickname }
                .ifBlank { fileAs }
                .ifBlank { org }
                .ifBlank { phones.firstOrNull().orEmpty() }
                .ifBlank { emails.firstOrNull().orEmpty() }

            if (rawName.isBlank() && phones.isEmpty() && emails.isEmpty()) {
                skipped++
                continue
            }

            val rawLabels = splitGoogleMulti(col("Labels")) +
                listOfNotNull(defaultCategory?.takeIf { it.isNotBlank() })

            val addressParts = addressIndices
                .mapNotNull { idx -> row.getOrNull(idx)?.trim()?.takeIf { it.isNotBlank() } }
                .distinct()
            val address = addressParts.joinToString(" - ").ifBlank { null }

            val (nestedCategories, isStarred) = SmartCategoryEngine.normalizeAndNestLabels(
                rawLabels = rawLabels,
                displayName = rawName,
                phones = phones,
                emails = emails,
                organization = org.ifBlank { null },
                jobTitle = title.ifBlank { null },
                notes = notes.ifBlank { null },
                address = address,
            )
            nestedCategories.forEach { addHierarchySegments(it, categories) }

            val geoMatch = EgyptGeoLookup.inferLocation(address, rawName, notes, rawLabels.joinToString(" "))
                ?: phones.firstOrNull()?.let { EgyptGeoLookup.inferLocationFromPhone(it) }
            val contactId = ContactDeduplicator.deterministicContactId(phones, emails, rawName)
            val enriched = ContactDeduplicator.enrichSingleContact(
                ContactRecord(
                    id = contactId,
                    displayName = rawName.take(200),
                    nickname = nickname.ifBlank { null },
                    primaryPhone = phones.firstOrNull().orEmpty(),
                    phones = phones,
                    emails = emails,
                    categoryPaths = nestedCategories,
                    organization = org.ifBlank { null },
                    jobTitle = title.ifBlank { null },
                    notes = notes.ifBlank { null },
                    address = address,
                    district = geoMatch?.key,
                    latitude = geoMatch?.lat,
                    longitude = geoMatch?.lng,
                    website = website,
                    isFavorite = isStarred,
                )
            )
            enriched.categoryPaths.forEach { addHierarchySegments(it, categories) }
            contacts += enriched
        }

        return ParsedDataset(contacts, skipped, categories)
    }

    private fun parseGenericRows(
        headers: List<String>,
        dataRows: List<List<String>>,
        defaultCategory: String?,
    ): ParsedDataset {
        val nameCol = findCol(headers, listOf("name", "full name", "الاسم", "displayname", "student"))
        val phoneCol = findCol(headers, listOf("phone", "mobile", "tel", "رقم", "الهاتف", "الموبايل", "واتساب"))
        val emailCol = findCol(headers, listOf("email address", "email", "e-mail", "البريد", "الإيميل"))
        val linkCol = findCol(headers, listOf("link", "whatsapp", "wa.me"))
        val deptCol = findCol(headers, listOf("القسم", "department", "category", "فئة"))
        val batchCol = findCol(headers, listOf("دفعة", "batch", "year"))
        val idCol = findCol(headers, listOf("id", "id 🪪"))

        val contacts = ArrayList<ContactRecord>(dataRows.size)
        val categories = LinkedHashSet<String>()
        var skipped = 0

        for (row in dataRows) {
            val rawName = nameCol?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val rawPhone = phoneCol?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val rawEmail = emailCol?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val rawLink = linkCol?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val dept = deptCol?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val batch = batchCol?.let { row.getOrNull(it)?.trim() }.orEmpty()
            val extId = idCol?.let { row.getOrNull(it)?.trim() }.orEmpty()

            val linkPhone = Regex("""wa\.me/(\d{8,15})""").find(rawLink)?.groupValues?.getOrNull(1).orEmpty()
            // Also scan entire row if neither primary phoneCol nor link had a valid phone
            val fallbackRowPhone = if (rawPhone.isBlank() && linkPhone.isBlank()) {
                row.firstNotNullOfOrNull { cell ->
                    PhoneUtils.extractPhonesFromSharedText(cell).firstOrNull()
                }.orEmpty()
            } else ""

            val normalizedPhone = PhoneUtils.normalizePhone(
                rawPhone.ifBlank { linkPhone }.ifBlank { fallbackRowPhone }
            )
            if (normalizedPhone.isBlank() && rawEmail.isBlank()) {
                skipped++
                continue
            }

            val displayName = rawName.ifBlank {
                if (extId.isNotBlank() && extId != ".") "طالب $extId" else normalizedPhone.ifBlank { rawEmail }
            }

            val base = defaultCategory ?: "الجامعة > الأكاديمية (EAEAT)"
            val catParts = buildList {
                add(base)
                if (batch.isNotBlank() && batch != "0") add("دفعة $batch")
                if (dept.isNotBlank()) add(dept)
            }
            val fullPath = catParts.joinToString(" > ")

            val (nestedCats, starred) = SmartCategoryEngine.normalizeAndNestLabels(
                rawLabels = listOf(fullPath),
                displayName = displayName,
                phones = listOfNotNull(normalizedPhone.takeIf { it.isNotBlank() }),
                emails = listOfNotNull(rawEmail.takeIf { it.contains("@") }),
                organization = if (base.contains("EAEAT")) "EAEAT Academy" else null,
                jobTitle = null,
                notes = if (extId.isNotBlank() && extId != ".") "ID: $extId" else null,
                address = null,
            )
            nestedCats.forEach { addHierarchySegments(it, categories) }

            val geo = EgyptGeoLookup.inferLocation(displayName, fullPath, "eaeat")
            val enriched = ContactDeduplicator.enrichSingleContact(
                ContactRecord(
                    id = ContactDeduplicator.deterministicContactId(
                        listOfNotNull(normalizedPhone.takeIf { it.isNotBlank() }),
                        listOfNotNull(rawEmail.takeIf { it.contains("@") }),
                        displayName,
                    ),
                    displayName = displayName.take(200),
                    primaryPhone = normalizedPhone,
                    phones = listOfNotNull(normalizedPhone.takeIf { it.isNotBlank() }),
                    emails = listOfNotNull(rawEmail.takeIf { it.contains("@") }),
                    categoryPaths = nestedCats,
                    organization = if (base.contains("EAEAT")) "EAEAT Academy" else null,
                    notes = if (extId.isNotBlank() && extId != ".") "ID: $extId" else null,
                    district = geo?.key,
                    latitude = geo?.lat,
                    longitude = geo?.lng,
                    isFavorite = starred,
                )
            )
            enriched.categoryPaths.forEach { addHierarchySegments(it, categories) }
            contacts += enriched
        }
        return ParsedDataset(contacts, skipped, categories)
    }

    private fun findCol(headers: List<String>, candidates: List<String>): Int? {
        for (candidate in candidates) {
            val idx = headers.indexOfFirst { it.trim().equals(candidate, ignoreCase = true) }
            if (idx >= 0) return idx
        }
        for (candidate in candidates) {
            val idx = headers.indexOfFirst { it.trim().contains(candidate, ignoreCase = true) }
            if (idx >= 0) return idx
        }
        return null
    }

    private fun splitGoogleMulti(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(":::")
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun addHierarchySegments(fullPath: String, out: MutableSet<String>) {
        val parts = fullPath.split(">").map { it.trim() }.filter { it.isNotBlank() }
        var current = ""
        for (part in parts) {
            current = if (current.isEmpty()) part else "$current > $part"
            out += current
        }
    }

    private fun readAllCsvRows(reader: BufferedReader): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val currentRow = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false

        while (true) {
            val line = reader.readLine() ?: break
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (inQuotes) {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(c)
                    }
                } else {
                    when (c) {
                        '"' -> inQuotes = true
                        ',' -> {
                            currentRow.add(field.toString())
                            field.setLength(0)
                        }
                        else -> field.append(c)
                    }
                }
                i++
            }
            if (inQuotes) {
                field.append('\n')
            } else {
                currentRow.add(field.toString())
                field.setLength(0)
                if (currentRow.any { it.isNotEmpty() }) {
                    rows.add(currentRow.toList())
                }
                currentRow.clear()
            }
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            rows.add(currentRow.toList())
        }
        return rows
    }

    fun exportToCsv(contacts: List<ContactRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("First Name,Nickname,Organization Name,Organization Title,Notes,Labels,Tags,E-mail 1 - Value,Phone 1 - Value,Address 1 - Formatted,Website 1 - Value")
        for (c in contacts) {
            val cols = listOf(
                c.displayName,
                c.nickname.orEmpty(),
                c.organization.orEmpty(),
                c.jobTitle.orEmpty(),
                c.notes.orEmpty(),
                c.categoryPaths.joinToString(" ::: "),
                c.tags.joinToString(" ::: "),
                c.emails.joinToString(" ::: "),
                c.phones.joinToString(" ::: "),
                c.address.orEmpty(),
                c.website.orEmpty(),
            ).map { escapeCsv(it) }
            sb.appendLine(cols.joinToString(","))
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
