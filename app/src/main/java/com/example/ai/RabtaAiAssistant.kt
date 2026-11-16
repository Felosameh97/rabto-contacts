package com.example.ai

import android.util.Log
import com.example.domain.model.ContactRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "RabtaAiAssistant"

enum class AiCommandAction {
    CALL,
    WHATSAPP,
    SEARCH,
    ADD_CONTACT,
    OPEN_CATEGORY,
    MAP_DISTRICT,
    PAYMENT,
    AUTO_CATEGORIZE,
    ANSWER,
}

data class AiCommandPlan(
    val action: AiCommandAction,
    val queryNameOrPhone: String = "",
    val messageText: String = "",
    val categoryPath: String = "",
    val district: String = "",
    val amount: String = "",
    val paymentMethod: String = "",
    val newContactName: String = "",
    val newContactPhone: String = "",
    val replyAr: String = "",
)

class RabtaAiAssistant {

    private val commandSchema = Schema.obj(
        properties = mapOf(
            "action" to Schema.enumeration(
                values = listOf(
                    "CALL",
                    "WHATSAPP",
                    "SEARCH",
                    "ADD_CONTACT",
                    "OPEN_CATEGORY",
                    "MAP_DISTRICT",
                    "PAYMENT",
                    "AUTO_CATEGORIZE",
                    "ANSWER",
                ),
                description = "The single best action matching the user's request",
            ),
            "queryNameOrPhone" to Schema.string(description = "Name, profession, keyword, or phone number to find in the user's contacts"),
            "messageText" to Schema.string(description = "Optional WhatsApp message text if the user asked to send a message"),
            "categoryPath" to Schema.string(description = "Nested Arabic category path using ' > ' separator if requesting a category"),
            "district" to Schema.string(description = "Egyptian district/city name in Arabic if searching by location or map"),
            "amount" to Schema.string(description = "Numeric transfer amount in EGP if requesting a payment transfer"),
            "paymentMethod" to Schema.string(description = "Wallet/bank name e.g. Vodafone Cash, InstaPay, Orange Cash, Etisalat Cash"),
            "newContactName" to Schema.string(description = "Name when action is ADD_CONTACT"),
            "newContactPhone" to Schema.string(description = "Phone number when action is ADD_CONTACT"),
            "replyAr" to Schema.string(description = "Friendly, concise Egyptian Arabic response summarizing what will be done"),
        ),
        optionalProperties = listOf(
            "queryNameOrPhone",
            "messageText",
            "categoryPath",
            "district",
            "amount",
            "paymentMethod",
            "newContactName",
            "newContactPhone",
        ),
    )

    private val classifySchema = Schema.obj(
        properties = mapOf(
            "assignments" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string(description = "Exact contact id provided in input"),
                        "categoryPath" to Schema.string(
                            description = "Clean nested Arabic category path using ' > ', e.g. 'الجامعة > الأكاديمية (EAEAT) > دفعة 2023' or 'المهن والخدمات > أطباء ودكاترة' or 'المناطق والمدن > المطرية' or 'الدائرة الخاصة > العائلة'"
                        ),
                    )
                ),
                description = "Assigned nested category paths for the contacts",
            )
        )
    )

    private val commandModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = commandSchema
                temperature = 0.2f
            },
        )
    }

    private val commandFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = commandSchema
                temperature = 0.2f
            },
        )
    }

    private val classifyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = classifySchema
                temperature = 0.15f
            },
        )
    }

    suspend fun parseCommand(userText: String, availableTopCategories: List<String>): Result<AiCommandPlan> {
        return try {
            val prompt = buildString {
                appendLine("أنت المساعد الذكي لتطبيق إدارة جهات الاتصال والمكالمات 'Felo Contacts' في مصر.")
                appendLine("المستخدم أصدر الأمر التالي باللغة العربية أو الإنجليزية:")
                appendLine("\"$userText\"")
                appendLine()
                appendLine("الأقسام الرئيسية المتاحة حالياً: ${availableTopCategories.joinToString(" | ")}")
                appendLine("اختر action المناسب بدقة:")
                appendLine("- CALL: إذا أراد الاتصال بشخص أو جهة")
                appendLine("- WHATSAPP: إذا أراد مراسلة أو إرسال رسالة واتساب لشخص")
                appendLine("- SEARCH: إذا أراد البحث عن رقم أو مهنة (مثل كهربائي في المطرية)")
                appendLine("- ADD_CONTACT: إذا أراد حفظ/إضافة جهة اتصال جديدة بالاسم والرقم")
                appendLine("- OPEN_CATEGORY: إذا أراد فتح تصنيف/ليبل معين (مثال: الجامعة > الأكاديمية (EAEAT) > دفعة 2023)")
                appendLine("- PAYMENT: إذا أراد تحويل مبلغ فودافون كاش / انستاباي / أورانج كاش لشخص")
                appendLine("- AUTO_CATEGORIZE: إذا طلب تصنيف أو تنظيم الأرقام غير المصنفة بالذكاء الاصطناعي")
                appendLine("- ANSWER: لأي سؤال عام أو استفسار نصي")
                appendLine("أرجع JSON مطابق للمخطط المطلوب فقط.")
            }

            val response = withTimeout(45_000L) {
                try {
                    commandModel.generateContent(prompt)
                } catch (primaryErr: Exception) {
                    if (isQuotaOrRateError(primaryErr)) {
                        commandFallbackModel.generateContent(prompt)
                    } else {
                        throw primaryErr
                    }
                }
            }
            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("أرجع Gemini رداً فارغاً"))

            val json = JSONObject(jsonString)
            val actionStr = json.optString("action", "ANSWER")
            val action = runCatching { AiCommandAction.valueOf(actionStr) }.getOrDefault(AiCommandAction.ANSWER)
            Result.success(
                AiCommandPlan(
                    action = action,
                    queryNameOrPhone = json.optString("queryNameOrPhone", "").trim(),
                    messageText = json.optString("messageText", "").trim(),
                    categoryPath = json.optString("categoryPath", "").trim(),
                    district = json.optString("district", "").trim(),
                    amount = json.optString("amount", "").trim(),
                    paymentMethod = json.optString("paymentMethod", "").trim(),
                    newContactName = json.optString("newContactName", "").trim(),
                    newContactPhone = json.optString("newContactPhone", "").trim(),
                    replyAr = json.optString("replyAr", "تم تنفيذ طلبك").trim(),
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini command timed out", e)
            Result.failure(IllegalStateException("انتهت مهلة الاتصال بمساعد Gemini (60 ثانية). حاول مرة أخرى."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini command failed", e)
            Result.failure(formatAiError(e))
        }
    }

    private val bilingualTagSchema = Schema.obj(
        properties = mapOf(
            "items" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "id" to Schema.string(description = "Exact contact id provided"),
                        "englishName" to Schema.string(description = "Full detailed name in natural English"),
                        "arabicNickname" to Schema.string(description = "Full Arabic name and any short/alternate name preserved"),
                        "tags" to Schema.array(
                            items = Schema.string(),
                            description = "3 to 7 short bilingual searchable tags (profession, university, batch, district, role)",
                        ),
                    )
                ),
                description = "Enriched bilingual contact records",
            )
        )
    )

    private val enrichModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = bilingualTagSchema
                temperature = 0.2f
            },
        )
    }

    data class BilingualTagEnrichment(
        val englishName: String,
        val arabicNickname: String,
        val tags: List<String>,
    )

    suspend fun enrichContactsBilingualAndTags(
        contacts: List<ContactRecord>,
    ): Result<Map<String, BilingualTagEnrichment>> {
        if (contacts.isEmpty()) return Result.success(emptyMap())
        val batch = contacts.take(35)
        return try {
            val prompt = buildString {
                appendLine("حول جهات الاتصال التالية إلى نظام ثنائي اللغة للبحث السريع:")
                appendLine("1) englishName: الاسم الكامل المفصل بالإنجليزية (مثلاً 'Dr. Ahmed Mohamed Ali' أو 'Eng. Kareem Hassan').")
                appendLine("2) arabicNickname: الاسم بالعربية مع الحفاظ على أي لقب أو اختصار قديم (مثلاً 'د. أحمد محمد علي').")
                appendLine("3) tags: من 3 إلى 7 تاجز مختصرة بالعربية والإنجليزية تصف مجال الشخص، دفعته، جامعته، مهنته، منطقته، أو أي معلومة عنه.")
                appendLine()
                for (c in batch) {
                    val info = listOfNotNull(
                        "id=${c.id}",
                        "name=${c.displayName}",
                        c.nickname?.let { "nick=$it" },
                        c.organization?.let { "org=$it" },
                        c.jobTitle?.let { "title=$it" },
                        c.categoryPaths.take(2).takeIf { it.isNotEmpty() }?.let { "cat=${it.joinToString(",")}" },
                        c.district?.let { "dist=$it" },
                        c.notes?.take(80)?.let { "notes=$it" },
                    ).joinToString(" | ")
                    appendLine(info)
                }
            }

            val response = withTimeout(60_000L) {
                enrichModel.generateContent(prompt)
            }
            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("أرجع Gemini رداً فارغاً أثناء التحديث ثنائي اللغة"))

            val json = JSONObject(jsonString)
            val arr = json.optJSONArray("items") ?: return Result.success(emptyMap())
            val out = LinkedHashMap<String, BilingualTagEnrichment>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val en = obj.optString("englishName").trim()
                val ar = obj.optString("arabicNickname").trim()
                val tagArr = obj.optJSONArray("tags")
                val tagList = buildList {
                    if (tagArr != null) {
                        for (j in 0 until tagArr.length()) {
                            val t = tagArr.optString(j).trim()
                            if (t.isNotBlank()) add(t)
                        }
                    }
                }
                if (id.isNotBlank() && en.isNotBlank()) {
                    out[id] = BilingualTagEnrichment(en, ar, tagList)
                }
            }
            Result.success(out)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini bilingual enrichment timed out", e)
            Result.failure(IllegalStateException("انتهت مهلة التحديث الذكي. حاول مرة أخرى."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini bilingual enrichment failed", e)
            Result.failure(formatAiError(e))
        }
    }

    suspend fun classifyContactBatch(contacts: List<ContactRecord>): Result<Map<String, String>> {
        if (contacts.isEmpty()) return Result.success(emptyMap())
        val batch = contacts.take(40)
        return try {
            val prompt = buildString {
                appendLine("صنف جهات الاتصال المصرية التالية إلى مسارات فئات عربية متداخلة واضحة باستخدام ' > '.")
                appendLine("أمثلة على الفئات: 'الجامعة > الأكاديمية (EAEAT) > دفعة 2023'، 'المهن والخدمات > أطباء ودكاترة'، 'المهن والخدمات > فنيون وصيانة وميكانيكا'، 'المهن والخدمات > مطاعم وتوصيل'، 'الخدمات الحكومية والأمن > شرطة وأمن'، 'الدائرة الخاصة > العائلة'، 'الدائرة الخاصة > الأصدقاء'، 'المناطق والمدن > المطرية'، 'أعمال وشركات > عملاء وموردون'.")
                appendLine("لا تضع تصنيف 'جهات اتصال تلقائية'؛ استنتج أفضل تصنيف واقعي من الاسم والجهة والملاحظات والإيميل.")
                appendLine()
                for (c in batch) {
                    val info = listOfNotNull(
                        "id=${c.id}",
                        "name=${c.displayName}",
                        c.organization?.let { "org=$it" },
                        c.jobTitle?.let { "title=$it" },
                        c.emails.firstOrNull()?.let { "email=$it" },
                        c.address?.let { "addr=$it" },
                        c.notes?.take(80)?.let { "notes=$it" },
                    ).joinToString(" | ")
                    appendLine(info)
                }
            }

            val response = withTimeout(60_000L) {
                classifyModel.generateContent(prompt)
            }
            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("أرجع Gemini رداً فارغاً أثناء التصنيف"))

            val json = JSONObject(jsonString)
            val arr = json.optJSONArray("assignments")
                ?: return Result.success(emptyMap())

            val resultMap = LinkedHashMap<String, String>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val path = item.optString("categoryPath").trim()
                    .replace("/", ">")
                    .split(">")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString(" > ")
                if (id.isNotBlank() && path.isNotBlank()) {
                    resultMap[id] = path
                }
            }
            Result.success(resultMap)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini classify timed out", e)
            Result.failure(IllegalStateException("انتهت مهلة تصنيف Gemini. حاول مرة أخرى."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini classify failed", e)
            Result.failure(formatAiError(e))
        }
    }

    data class CallAnalysisResult(
        val transcript: String,
        val highlights: List<String>,
        val extractedNotes: String,
        val eventTitle: String,
        val eventDateIso: String,
        val eventEpochMs: Long?,
        val suggestedCategory: String,
    )

    private val callAnalysisSchema = Schema.obj(
        properties = mapOf(
            "transcript" to Schema.string(description = "Full accurate transcription of the phone call in the original spoken language (Arabic/English)"),
            "highlights" to Schema.array(
                items = Schema.string(),
                description = "2 to 5 concise bullet highlights (commitments, amounts, locations, important details)",
            ),
            "extractedNotes" to Schema.string(description = "Organized summary note to append to the contact record"),
            "eventTitle" to Schema.string(description = "Short calendar event title if an appointment/meeting/reminder was mentioned, otherwise empty string"),
            "eventDateIso" to Schema.string(description = "Local date-time in 'yyyy-MM-dd HH:mm' format relative to current timestamp if an event was mentioned, otherwise empty string"),
            "suggestedCategory" to Schema.string(description = "Optional nested Arabic category suggestion using ' > '"),
        ),
        optionalProperties = listOf("eventTitle", "eventDateIso", "suggestedCategory"),
    )

    private val callAnalysisModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = callAnalysisSchema
                temperature = 0.2f
            },
        )
    }

    private val callAnalysisFallbackModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = callAnalysisSchema
                temperature = 0.2f
            },
        )
    }

    suspend fun analyzeCallRecording(
        audioFile: File,
        contactName: String,
        phone: String,
    ): Result<CallAnalysisResult> {
        return try {
            if (!audioFile.exists() || audioFile.length() < 512L) {
                return Result.failure(IllegalStateException("ملف التسجيل الصوتي غير موجود أو فارغ"))
            }
            if (audioFile.length() > 18L * 1024L * 1024L) {
                return Result.failure(IllegalStateException("حجم التسجيل أكبر من حد 18 ميجا للتحليل الفوري"))
            }
            val bytes = withContext(Dispatchers.IO) { audioFile.readBytes() }
            val nowIso = SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.US).format(Date())

            val prompt = content {
                inlineData(bytes, "audio/mp4")
                text(
                    buildString {
                        appendLine("قم بتفريغ (Transcribe) هذه المكالمة الهاتفية بالكامل بدقة باللغة الأصلية (مصري/عربي أو إنجليزي).")
                        appendLine("بيانات المتصل: $contactName ($phone)")
                        appendLine("التاريخ والوقت الحالي المرجعي: $nowIso")
                        appendLine()
                        appendLine("المطلوب إرجاع JSON منظم يشمل:")
                        appendLine("1) transcript: النص الكامل للمكالمة.")
                        appendLine("2) highlights: أهم النقاط والمواعيد والأرقام والالتزامات (2-5 جمل قصيرة).")
                        appendLine("3) extractedNotes: ملاحظات منظمة ومختصرة لحفظها داخل ملف جهة الاتصال.")
                        appendLine("4) eventTitle و eventDateIso: إذا ذُكر موعد أو تذكير (مثلاً 'بكرة عندنا كذا' أو 'يوم الخميس الساعة 5')، استخرج عنوان الحدث وتاريخه بصيغة yyyy-MM-dd HH:mm، وإلا اتركهما فارغين.")
                        appendLine("5) suggestedCategory: اقتراح تصنيف مناسب لجهة الاتصال إن وضح من المكالمة.")
                    }
                )
            }

            val response = withTimeout(60_000L) {
                try {
                    callAnalysisModel.generateContent(prompt)
                } catch (primaryErr: Exception) {
                    if (isQuotaOrRateError(primaryErr)) {
                        callAnalysisFallbackModel.generateContent(prompt)
                    } else {
                        throw primaryErr
                    }
                }
            }
            val jsonString = response.text
                ?: return Result.failure(IllegalStateException("أرجع Gemini رداً فارغاً أثناء تحليل المكالمة"))
            val json = JSONObject(jsonString)
            val highlightsArr = json.optJSONArray("highlights")
            val highlights = buildList {
                if (highlightsArr != null) {
                    for (i in 0 until highlightsArr.length()) {
                        val h = highlightsArr.optString(i).trim()
                        if (h.isNotBlank()) add(h)
                    }
                }
            }
            val eventDateIso = json.optString("eventDateIso", "").trim()
            Result.success(
                CallAnalysisResult(
                    transcript = json.optString("transcript", "").trim(),
                    highlights = highlights,
                    extractedNotes = json.optString("extractedNotes", "").trim(),
                    eventTitle = json.optString("eventTitle", "").trim(),
                    eventDateIso = eventDateIso,
                    eventEpochMs = parseIsoDateToEpochMs(eventDateIso),
                    suggestedCategory = json.optString("suggestedCategory", "").trim(),
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini audio transcription timed out", e)
            Result.failure(IllegalStateException("انتهت مهلة تفريغ المكالمة (60 ثانية)."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini audio transcription failed", e)
            Result.failure(formatAiError(e))
        }
    }

    companion object {
        fun isQuotaOrRateError(e: Throwable): Boolean {
            val raw = (e.localizedMessage ?: e.message.orEmpty()).lowercase(Locale.US)
            return raw.contains("quota") || raw.contains("exceeded") || raw.contains("resource_exhausted") || raw.contains("429") || raw.contains("rate limit")
        }

        private fun formatAiError(e: Exception): Exception {
            val raw = e.localizedMessage ?: e.message.orEmpty()
            val lower = raw.lowercase(Locale.US)
            return when {
                isQuotaOrRateError(e) -> IllegalStateException(
                    "رصيد طلبات Gemini السحابي وصل للحد المجاني حالياً (Quota Exceeded).\n" +
                        "• استخدم «التفريغ الصوتي المباشر» من الرئيسية أو شاشة المكالمة مجاناً وبدون Gemini.\n" +
                        "• التصنيف الذكي والبحث والأوامر السريعة شغالة محلياً بدون أي رصيد.",
                    e,
                )
                lower.contains("app check") || lower.contains("token is invalid") || lower.contains("403") -> IllegalStateException(
                    "$raw\n\nتأكد من اتصال الإنترنت ووجود Google Play Services، أو أعد تشغيل التطبيق.",
                    e,
                )
                else -> e
            }
        }

        fun parseIsoDateToEpochMs(iso: String): Long? {
            if (iso.isBlank()) return null
            val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")
            for (p in patterns) {
                val parsed = runCatching {
                    SimpleDateFormat(p, Locale.US).parse(iso.take(p.length.coerceAtMost(iso.length)))
                }.getOrNull()
                if (parsed != null) return parsed.time
            }
            return null
        }
    }
}
