package com.chatai.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de données ChatAI Intelligence System
 * Stocke toutes les conversations pour mémoire persistante et apprentissage
 */
@Database(
    entities = [ConversationEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChatAIDatabase : RoomDatabase() {
    
    abstract fun conversationDao(): ConversationDao
    
    companion object {
        @Volatile
        private var INSTANCE: ChatAIDatabase? = null
        
        fun getDatabase(context: Context): ChatAIDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ChatAIDatabase::class.java,
                    "chatai_intelligence.db"
                )
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Base de données créée
                        android.util.Log.d("ChatAIDatabase", "Database created - ChatAI Intelligence System ready")
                    }
                    
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        android.util.Log.d("ChatAIDatabase", "Database opened")
                    }
                })
                .fallbackToDestructiveMigration() // Pour le développement - À ENLEVER en production
                .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Ferme la base de données (pour tests ou nettoyage)
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

