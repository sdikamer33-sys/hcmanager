package com.example.hcmanager

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class ConfigStatus(val label: String) {
    WORKING("شغال"),
    OLD("قديم"),
    UNTESTED("غير مفحوص");

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: UNTESTED
    }
}

@Entity(tableName = "configs")
data class ConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val addedAt: Long,
    val note: String = "",
    val status: String = ConfigStatus.UNTESTED.name
)

@Dao
interface ConfigDao {
    @Query("SELECT * FROM configs ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ConfigEntity>>

    @Query("SELECT * FROM configs WHERE id = :id")
    fun observe(id: Long): Flow<ConfigEntity?>

    @Insert
    suspend fun insert(c: ConfigEntity): Long

    @Query("UPDATE configs SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String)

    @Query("UPDATE configs SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Delete
    suspend fun delete(c: ConfigEntity)
}

@Database(entities = [ConfigEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ConfigDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(
                ctx.applicationContext, AppDatabase::class.java, "configs.db"
            ).build().also { inst = it }
        }
    }
}
