package android.zero.studio.chatai.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.zero.studio.chatai.data.database.dao.ChatRoomDao
import android.zero.studio.chatai.data.database.dao.MessageDao
import android.zero.studio.chatai.data.database.entity.APITypeConverter
import android.zero.studio.chatai.data.database.entity.ChatRoom
import android.zero.studio.chatai.data.database.entity.Message

@Database(entities = [ChatRoom::class, Message::class], version = 1)
@TypeConverters(APITypeConverter::class)
abstract class ChatDatabase : RoomDatabase() {

    abstract fun chatRoomDao(): ChatRoomDao
    abstract fun messageDao(): MessageDao
}
