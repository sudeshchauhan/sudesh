package com.example.spendwise

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object Backup {
    suspend fun export(c: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val d = AppDb.get(c).dao()
        val o = JSONObject()
        o.put("txns", JSONArray(d.allTxns().map {
            JSONObject().put("amount", it.amount).put("expense", it.isExpense).put("category", it.category)
                .put("note", it.note).put("date", it.date).put("smsKey", it.smsKey ?: JSONObject.NULL)
        }))
        o.put("budgets", JSONArray(d.allBudgets().map {
            JSONObject().put("category", it.category).put("limit", it.monthlyLimit) }))
        o.put("categories", JSONArray(d.allCats().map { it.name }))
        c.contentResolver.openOutputStream(uri)!!.use { it.write(o.toString(2).toByteArray()) }
    }

    /** Replaces all current data with the backup. Returns number of transactions restored. */
    suspend fun restore(c: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = c.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val o = JSONObject(text)
        val tx = o.getJSONArray("txns").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.map {
            Txn(amount = it.getDouble("amount"), isExpense = it.getBoolean("expense"), category = it.getString("category"),
                note = it.getString("note"), date = it.getLong("date"), smsKey = it.optString("smsKey").takeIf { s -> s.isNotEmpty() && s != "null" })
        }
        val bg = o.getJSONArray("budgets").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .map { Budget(it.getString("category"), it.getDouble("limit")) }
        val cats = o.getJSONArray("categories").let { a -> (0 until a.length()).map { Category(a.getString(it)) } }
        val db = AppDb.get(c); val d = db.dao()
        db.withTransaction {
            d.clearTxns(); d.clearBudgets(); d.clearCats()
            d.addCats(cats); d.setBudgets(bg); d.insertTxns(tx)
        }
        tx.size
    }
}
