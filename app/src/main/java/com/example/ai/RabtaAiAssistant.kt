package com.example.ai

import android.util.Log
import com.example.domain.model.ContactRecord
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

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
            modelName = "gemini-3.8-flash",
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
                appendLine("أنت المساعد الذكي لتطبيق إدارة جهات الاتصال والاتصال 'رابطة' (Rabta Contacts) في مصر.")
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
                appendLine("- MAP_DISTRICT: إذا أراد إظهار جهات اتصال منطقة معينة على الخريطة")
                appendLine("- PAYMENT: إذا أراد تحويل مبلغ فودافون كاش / انستاباي / أورانج كاش لشخص")
                appendLine("- AUTO_CATEGORIZE: إذا طلب تصنيف أو تنظيم الأرقام غير المصنفة بالذكاء الاصطناعي")
                appendLine("- ANSWER: لأي سؤال عام أو استفسار نصي")
                appendLine("أرجع JSON مطابق للمخطط المطلوب فقط.")
            }

            val response = withTimeout(60_000L) {
                commandModel.generateContent(prompt)
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
            Result.failure(e)
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
            modelName = "gemini-3.8-flash",
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
            Result.failure(e)
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
            Result.failure(e)
        }
    }
}
