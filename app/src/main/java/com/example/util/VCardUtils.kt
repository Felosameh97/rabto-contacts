package com.example.util

import com.example.domain.model.ContactRecord
import java.util.UUID

object VCardUtils {

    fun toVCard(contact: ContactRecord): String = buildString {
        appendLine("BEGIN:VCARD")
        appendLine("VERSION:3.0")
        appendLine("FN:${escapeVCard(contact.displayName)}")
        appendLine("N:${escapeVCard(contact.displayName)};;;;")
        contact.nickname?.takeIf { it.isNotBlank() }?.let {
            appendLine("NICKNAME:${escapeVCard(it)}")
        }
        contact.phones.ifEmpty { listOfNotNull(contact.primaryPhone.takeIf { it.isNotBlank() }) }
            .forEachIndexed { idx, phone ->
                val type = if (idx == 0) "CELL" else "VOICE"
                appendLine("TEL;TYPE=$type:$phone")
            }
        contact.emails.forEach { email ->
            appendLine("EMAIL;TYPE=INTERNET:$email")
        }
        contact.organization?.takeIf { it.isNotBlank() }?.let {
            appendLine("ORG:${escapeVCard(it)}")
        }
        contact.jobTitle?.takeIf { it.isNotBlank() }?.let {
            appendLine("TITLE:${escapeVCard(it)}")
        }
        contact.address?.takeIf { it.isNotBlank() }?.let {
            appendLine("ADR;TYPE=HOME:;;${escapeVCard(it)};;;;")
        }
        contact.website?.takeIf { it.isNotBlank() }?.let {
            appendLine("URL:$it")
        }
        val catsAndTags = (contact.categoryPaths + contact.tags).distinct()
        if (catsAndTags.isNotEmpty()) {
            appendLine("CATEGORIES:${catsAndTags.joinToString(",") { escapeVCard(it) }}")
        }
        contact.notes?.takeIf { it.isNotBlank() }?.let {
            appendLine("NOTE:${escapeVCard(it)}")
        }
        if (contact.latitude != null && contact.longitude != null) {
            appendLine("GEO:${contact.latitude};${contact.longitude}")
        }
        appendLine("END:VCARD")
    }

    fun exportAll(contacts: List<ContactRecord>): String =
        contacts.joinToString("\n") { toVCard(it).trimEnd() }

    fun parseVCards(rawText: String, skipEnrich: Boolean = false): List<ContactRecord> {
        return parseVCardStream(rawText.reader().buffered(), skipEnrich = skipEnrich)
    }

    /**
     * Streams arbitrary-size .vcf files line-by-line (supporting RFC folded lines) without holding
     * regex-split copies in memory, generating deterministic IDs for idempotent re-imports.
     */
    fun parseVCardStream(reader: java.io.BufferedReader, skipEnrich: Boolean = false): List<ContactRecord> {
        val results = ArrayList<ContactRecord>()
        var inCard = false
        var fn = ""
        var nickname: String? = null
        val phones = LinkedHashSet<String>()
        val emails = LinkedHashSet<String>()
        var org: String? = null
        var title: String? = null
        var address: String? = null
        var note: String? = null
        var website: String? = null
        val categories = LinkedHashSet<String>()
        var lat: Double? = null
        var lng: Double? = null

        fun flushCard() {
            if (fn.isBlank() && phones.isEmpty() && emails.isEmpty()) return
            val phoneList = phones.take(10)
            val emailList = emails.take(5)
            val resolvedName = fn.ifBlank { phoneList.firstOrNull().orEmpty() }.ifBlank { emailList.firstOrNull().orEmpty() }
            val geo = if (lat == null || lng == null) EgyptGeoLookup.inferLocation(address, resolvedName, note) else null
            val base = ContactRecord(
                id = ContactDeduplicator.deterministicContactId(phoneList, emailList, resolvedName),
                displayName = resolvedName.take(200),
                nickname = nickname,
                primaryPhone = phoneList.firstOrNull().orEmpty(),
                phones = phoneList,
                emails = emailList,
                categoryPaths = categories.take(15),
                organization = org,
                jobTitle = title,
                notes = note,
                address = address,
                district = geo?.key,
                latitude = lat ?: geo?.lat,
                longitude = lng ?: geo?.lng,
                website = website,
            )
            results += if (skipEnrich) base else ContactDeduplicator.enrichSingleContact(base)
        }

        fun resetCard() {
            fn = ""
            nickname = null
            phones.clear()
            emails.clear()
            org = null
            title = null
            address = null
            note = null
            website = null
            categories.clear()
            lat = null
            lng = null
        }

        fun processLogicalLine(line: String) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VCARD", ignoreCase = true)) {
                resetCard()
                inCard = true
                return
            }
            if (trimmed.equals("END:VCARD", ignoreCase = true)) {
                if (inCard) flushCard()
                inCard = false
                resetCard()
                return
            }
            if (!inCard) return

            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) return
            val key = line.substring(0, colonIdx).uppercase()
            val value = line.substring(colonIdx + 1).trim()
            if (value.isBlank()) return

            when {
                key == "FN" || key.startsWith("FN;") -> fn = unescapeVCard(value)
                key.startsWith("NICKNAME") -> nickname = unescapeVCard(value)
                (key == "N" || key.startsWith("N;")) && fn.isBlank() -> {
                    fn = value.split(";").filter { it.isNotBlank() }.joinToString(" ")
                }
                key.startsWith("TEL") -> {
                    val norm = PhoneUtils.normalizePhone(value)
                    if (norm.isNotBlank()) phones += norm
                }
                key.startsWith("EMAIL") -> if (value.contains("@")) emails += value
                key.startsWith("ORG") -> org = unescapeVCard(value.replace(";", " ").trim())
                key.startsWith("TITLE") -> title = unescapeVCard(value)
                key.startsWith("ADR") -> address = value.split(";").filter { it.isNotBlank() }.joinToString(", ")
                key.startsWith("NOTE") -> note = unescapeVCard(value)
                key.startsWith("URL") -> website = value
                key.startsWith("CATEGORIES") -> {
                    value.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { categories += it }
                }
                key.startsWith("GEO") -> {
                    val parts = value.split(";", ",")
                    lat = parts.getOrNull(0)?.toDoubleOrNull()
                    lng = parts.getOrNull(1)?.toDoubleOrNull()
                }
            }
        }

        val pendingLine = StringBuilder()
        while (true) {
            val raw = reader.readLine() ?: break
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && pendingLine.isNotEmpty()) {
                pendingLine.append(raw.substring(1))
            } else {
                if (pendingLine.isNotEmpty()) {
                    processLogicalLine(pendingLine.toString())
                    pendingLine.setLength(0)
                }
                pendingLine.append(raw)
            }
        }
        if (pendingLine.isNotEmpty()) {
            processLogicalLine(pendingLine.toString())
        }
        if (inCard) flushCard()
        return results
    }

    /**
     * Parses a scanned QR payload which may be a vCard, MECARD, tel:, wa.me link, or raw phone text.
     */
    fun parseQrPayload(payload: String): ContactRecord? {
        val trimmed = payload.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.contains("BEGIN:VCARD", ignoreCase = true)) {
            return parseVCards(trimmed).firstOrNull()
        }
        if (trimmed.startsWith("MECARD:", ignoreCase = true)) {
            val body = trimmed.removePrefix("MECARD:").removePrefix("mecard:")
            var name = ""
            var phone = ""
            var email = ""
            var note = ""
            for (token in body.split(";")) {
                when {
                    token.startsWith("N:", true) -> name = token.drop(2).trim()
                    token.startsWith("TEL:", true) -> phone = PhoneUtils.normalizePhone(token.drop(4))
                    token.startsWith("EMAIL:", true) -> email = token.drop(6).trim()
                    token.startsWith("NOTE:", true) -> note = token.drop(5).trim()
                }
            }
            if (name.isBlank() && phone.isBlank()) return null
            return ContactRecord(
                id = "c_" + UUID.randomUUID().toString().replace("-", "").take(16),
                displayName = name.ifBlank { phone },
                primaryPhone = phone,
                phones = listOfNotNull(phone.takeIf { it.isNotBlank() }),
                emails = listOfNotNull(email.takeIf { it.contains("@") }),
                notes = note.ifBlank { null },
            )
        }
        val phones = PhoneUtils.extractPhonesFromSharedText(trimmed)
        if (phones.isNotEmpty()) {
            return ContactRecord(
                id = "c_" + UUID.randomUUID().toString().replace("-", "").take(16),
                displayName = phones.first(),
                primaryPhone = phones.first(),
                phones = phones,
                notes = if (trimmed != phones.first()) trimmed else null,
            )
        }
        return null
    }

    private fun escapeVCard(s: String): String =
        s.replace("\\", "\\\\").replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")

    private fun unescapeVCard(s: String): String =
        s.replace("\\n", "\n").replace("\\;", ";").replace("\\,", ",").replace("\\\\", "\\")
}
