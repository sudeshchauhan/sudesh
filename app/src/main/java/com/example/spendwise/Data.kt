package com.example.spendwise

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

val DEFAULT_CATS = listOf("Food", "Transport", "Shopping", "Bills", "Entertainment", "Health", "Income", "Other")

@Entity(indices = [Index("smsKey", unique = true)])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double, val isExpense: Boolean = true, val category: String,
    val note: String = "", val date: Long = System.currentTimeMillis(), val smsKey: String? = null
)
@Entity data class Budget(@PrimaryKey val category: String, val monthlyLimit: Double)
@Entity data class Category(@PrimaryKey val name: String)

@Dao interface AppDao {
    @Query("SELECT * FROM Txn ORDER BY date DESC") fun txns(): Flow<List<Txn>>
    @Query("SELECT * FROM Txn") suspend fun allTxns(): List<Txn>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: Txn): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTxns(t: List<Txn>)
    @Delete suspend fun delete(t: Txn)
    @Query("DELETE FROM Txn") suspend fun clearTxns()

    @Query("SELECT * FROM Budget") fun budgets(): Flow<List<Budget>>
    @Query("SELECT * FROM Budget") suspend fun allBudgets(): List<Budget>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setBudget(b: Budget)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setBudgets(b: List<Budget>)
    @Query("DELETE FROM Budget") suspend fun clearBudgets()

    @Query("SELECT * FROM Category ORDER BY name") fun cats(): Flow<List<Category>>
    @Query("SELECT * FROM Category") suspend fun allCats(): List<Category>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addCat(c: Category)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addCats(c: List<Category>)
    @Query("DELETE FROM Category") suspend fun clearCats()
}

@Database(entities = [Txn::class, Budget::class, Category::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "spend.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_CATS.forEach { db.execSQL("INSERT INTO Category(name) VALUES('$it')") }
                    }
                }).build().also { inst = it }
        }
    }
}
