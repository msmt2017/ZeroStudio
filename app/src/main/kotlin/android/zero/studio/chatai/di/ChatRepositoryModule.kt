package android.zero.studio.chatai.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.zero.studio.chatai.data.database.dao.ChatRoomDao
import android.zero.studio.chatai.data.database.dao.MessageDao
import android.zero.studio.chatai.data.network.AnthropicAPI
import android.zero.studio.chatai.data.repository.ChatRepository
import android.zero.studio.chatai.data.repository.ChatRepositoryImpl
import android.zero.studio.chatai.data.repository.SettingRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatRepositoryModule {

    @Provides
    @Singleton
    fun provideChatRepository(
        @ApplicationContext appContext: Context,
        chatRoomDao: ChatRoomDao,
        messageDao: MessageDao,
        settingRepository: SettingRepository,
        anthropicAPI: AnthropicAPI
    ): ChatRepository = ChatRepositoryImpl(appContext, chatRoomDao, messageDao, settingRepository, anthropicAPI)
}
