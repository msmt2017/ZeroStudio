package me.rerere.rikkahub.di

import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import org.koin.dsl.module

val repositoryModule = module {
    single {
        ConversationRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }
}