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
    entities = [ConversationEntity::class, FactEntity::class],
    version = 4, // ⭐ v4: Ajout table kitt_facts pour RAG et extraction de faits
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChatAIDatabase : RoomDatabase() {
    
    abstract fun conversationDao(): ConversationDao
    abstract fun factDao(): FactDao
    
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
                .setJournalMode(RoomDatabase.JournalMode.AUTOMATIC) // ⭐ FIX: Mode automatique (Room gère le WAL)
                .addMigrations(MIGRATION_3_4) // ⭐ v4: Migration pour table kitt_facts
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        android.util.Log.d("ChatAIDatabase", "✅ Database created - ChatAI Intelligence System ready")
                    }
                    
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        android.util.Log.d("ChatAIDatabase", "✅ Database opened - Ready for conversations")
                    }
                })
                .fallbackToDestructiveMigration() // Pour le développement - À ENLEVER en production
                .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Migration v3 → v4: Ajout table kitt_facts pour RAG et extraction de faits
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                android.util.Log.d("ChatAIDatabase", "🔄 Migration v3 → v4: Création table kitt_facts")
                
                // Créer table kitt_facts
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS kitt_facts (
                        id TEXT NOT NULL PRIMARY KEY,
                        type TEXT NOT NULL,
                        content TEXT NOT NULL,
                        sourceConversationId TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        tags TEXT
                    )
                """)
                
                // Créer index sur type pour recherche rapide
                database.execSQL("CREATE INDEX IF NOT EXISTS index_kitt_facts_type ON kitt_facts(type)")
                
                // Créer index sur timestamp pour tri chronologique
                database.execSQL("CREATE INDEX IF NOT EXISTS index_kitt_facts_timestamp ON kitt_facts(timestamp)")
                
                // Créer index sur sourceConversationId pour traçabilité
                database.execSQL("CREATE INDEX IF NOT EXISTS index_kitt_facts_sourceConversationId ON kitt_facts(sourceConversationId)")
                
                android.util.Log.d("ChatAIDatabase", "✅ Migration v3 → v4 terminée: table kitt_facts créée")
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

