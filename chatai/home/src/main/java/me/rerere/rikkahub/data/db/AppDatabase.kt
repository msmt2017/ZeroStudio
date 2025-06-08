package me.rerere.rikkahub.data.db

import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.MemoryDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.db.entity.MemoryEntity
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.utils.JsonInstant

private const val TAG = "AppDatabase"

@Database(
    entities = [ConversationEntity::class, MemoryEntity::class],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ]
)
@TypeConverters(TokenUsageConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDAO

    abstract fun memoryDao(): MemoryDAO
}

object TokenUsageConverter {
    @TypeConverter
    fun fromTokenUsage(usage: TokenUsage?): String {
        return JsonInstant.encodeToString(usage)
    }

    @TypeConverter
    fun toTokenUsage(usage: String): TokenUsage? {
        return JsonInstant.decodeFromString(usage)
    }
}

val Migration_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "migrate: start migrate from 6 to 7")
        db.beginTransaction()
        try {
            // 创建新表结构（不包含messages列）
            db.execSQL("""
                CREATE TABLE ConversationEntity_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    assistant_id TEXT NOT NULL DEFAULT '0950e2dc-9bd5-4801-afa3-aa887aa36b4e',
                    title TEXT NOT NULL,
                    nodes TEXT NOT NULL,
                    usage TEXT,
                    create_at INTEGER NOT NULL,
                    update_at INTEGER NOT NULL,
                    truncate_index INTEGER NOT NULL DEFAULT -1
                )
            """.trimIndent())

            // 获取所有对话记录并转换数据
            val cursor = db.query("SELECT id, assistant_id, title, messages, usage, create_at, update_at, truncate_index FROM ConversationEntity")
            val updates = mutableListOf<Array<Any?>>()
            
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val assistantId = cursor.getString(1)
                val title = cursor.getString(2)
                val messagesJson = cursor.getString(3)
                val usage = cursor.getString(4)
                val createAt = cursor.getLong(5)
                val updateAt = cursor.getLong(6)
                val truncateIndex = cursor.getInt(7)
                
                try {
                    // 尝试解析旧格式的消息列表 List<UIMessage>
                    val oldMessages = JsonInstant.decodeFromString<List<UIMessage>>(messagesJson)
                    
                    // 转换为新格式 List<MessageNode>
                    val newMessages = oldMessages.map { message ->
                        MessageNode.of(message)
                    }
                    
                    // 序列化新格式
                    val newMessagesJson = JsonInstant.encodeToString(newMessages)
                    updates.add(arrayOf(id, assistantId, title, newMessagesJson, usage, createAt, updateAt, truncateIndex))
                } catch (e: Exception) {
                    // 如果解析失败，可能已经是新格式或者数据损坏，跳过
                    error("Failed to migrate messages for conversation $id: ${e.message}")
                }
            }
            cursor.close()
            
            // 批量插入数据到新表
            updates.forEach { values ->
                db.execSQL(
                    "INSERT INTO ConversationEntity_new (id, assistant_id, title, nodes, usage, create_at, update_at, truncate_index) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    values
                )
            }

            // 删除旧表
            db.execSQL("DROP TABLE ConversationEntity")
            
            // 重命名新表
            db.execSQL("ALTER TABLE ConversationEntity_new RENAME TO ConversationEntity")
            
            db.setTransactionSuccessful()

            Log.i(TAG, "migrate: migrate from 6 to 7 success (${updates.size} conversations updated)")
        } finally {
            db.endTransaction()
        }
    }
}