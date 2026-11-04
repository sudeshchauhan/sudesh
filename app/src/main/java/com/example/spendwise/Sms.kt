package com.example.spendwise

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.*

object SmsParser {
    private val amt = Regex("(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val debit = Regex("debited|spent|paid|purchase|withdrawn|sent", RegexOption.IGNORE_CASE)
    private val credit = Regex("credited|received|refund|deposited", RegexOption.IGNORE_CASE)
    private val skip = Regex("otp|one time password|offer|loan|pre-approved", RegexOption.IGNORE_CASE)
    private val who = Regex("(?:at|to|towards|vpa)\\s+([A-Za-z0-9 .&@_-]{3,30})", RegexOption.IGNORE_CASE)
    private val rules = mapOf(
        "Food" to "swiggy|zomato|restaurant|cafe|pizza|dominos|mcdonald|kfc|bigbasket|blinkit|zepto",
        "Transport" to "uber|ola|rapido|metro|irctc|fuel|petrol|hpcl|bpcl|iocl|fastag",
        "Shopping" to "amazon|flipkart|myntra|ajio|mall|mart",
        "Bills" to "electricity|recharge|airtel|jio|vodafone|bill|insurance|broadband|gas",
        "Entertainment" to "netflix|spotify|hotstar|bookmyshow|prime|youtube",
        "Health" to "pharmacy|apollo|hospital|clinic|medplus|1mg|pharmeasy")

    fun parse(sender: String, body: String, time: Long): Txn? {
        if (skip.containsMatchIn(body)) return null
        val a = amt.find(body)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null
        val d = debit.find(body)?.range?.first ?: Int.MAX_VALUE
        val c = credit.find(body)?.range?.first ?: Int.MAX_VALUE
        if (d == Int.MAX_VALUE && c == Int.MAX_VALUE) return null
        val expense = d < c
        val merchant = who.find(body)?.groupValues?.get(1)?.trim() ?: sender
        val cat = if (!expense) "Income" else rules.entries
            .firstOrNull { Regex(it.value, RegexOption.IGNORE_CASE).containsMatchIn(body) }?.key ?: "Other"
        return Txn(amount = a, isExpense = expense, category = cat, note = merchant,
            date = time, smsKey = "$time|${body.hashCode()}")
    }

    suspend fun scanInbox(c: Context, days: Int = 60): Int = withContext(Dispatchers.IO) {
        val dao = AppDb.get(c).dao()
        val since = System.currentTimeMillis() - days * 86_400_000L
        var n = 0
        c.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, arrayOf("address", "body", "date"),
            "date>?", arrayOf(since.toString()), "date DESC")?.use { cur ->
            while (cur.moveToNext()) {
                val t = parse(cur.getString(0) ?: "", cur.getString(1) ?: "", cur.getLong(2))
                if (t != null && dao.insert(t) != -1L) n++
            }
        }
        n
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(i)
        if (msgs.isNullOrEmpty()) return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val sender = msgs[0].originatingAddress ?: ""
        val time = msgs[0].timestampMillis
        val p = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { SmsParser.parse(sender, body, time)?.let { AppDb.get(c).dao().insert(it) } }
            finally { p.finish() }
        }
    }
}
