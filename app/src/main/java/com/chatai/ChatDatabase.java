package com.chatai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

/**
 * Base de données SQLite pour la persistance des conversations et du cache
 */
public class ChatDatabase extends SQLiteOpenHelper {
    private static final String TAG = "ChatDatabase";
    private static final String DATABASE_NAME = "chat_ai.db";
    private static final int DATABASE_VERSION = 1;
    
    // Tables
    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String TABLE_MESSAGES = "messages";
    private static final String TABLE_CACHE = "ai_cache";
    private static final String TABLE_SETTINGS = "settings";
    
    // Colonnes conversations
    private static final String COL_CONV_ID = "conversation_id";
    private static final String COL_CONV_TITLE = "title";
    private static final String COL_CONV_CREATED = "created_at";
    private static final String COL_CONV_UPDATED = "updated_at";
    
    // Colonnes messages
    private static final String COL_MSG_ID = "message_id";
    private static final String COL_MSG_CONV_ID = "conversation_id";
    private static final String COL_MSG_SENDER = "sender";
    private static final String COL_MSG_CONTENT = "content";
    private static final String COL_MSG_TIMESTAMP = "timestamp";
    private static final String COL_MSG_TYPE = "message_type";
    
    // Colonnes cache
    private static final String COL_CACHE_ID = "cache_id";
    private static final String COL_CACHE_PROMPT = "prompt";
    private static final String COL_CACHE_RESPONSE = "response";
    private static final String COL_CACHE_TIMESTAMP = "timestamp";
    
    // Colonnes settings
    private static final String COL_SETTINGS_KEY = "setting_key";
    private static final String COL_SETTINGS_VALUE = "setting_value";
    
    public ChatDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
        Log.i(TAG, "Base de données créée");
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migration future
        Log.i(TAG, "Mise à jour base de données de " + oldVersion + " vers " + newVersion);
    }
    
    /**
     * Crée toutes les tables
     */
    private void createTables(SQLiteDatabase db) {
        // Table conversations
        String createConversationsTable = 
            "CREATE TABLE " + TABLE_CONVERSATIONS + " (" +
            COL_CONV_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_CONV_TITLE + " TEXT NOT NULL, " +
            COL_CONV_CREATED + " INTEGER NOT NULL, " +
            COL_CONV_UPDATED + " INTEGER NOT NULL" +
            ")";
        
        // Table messages
        String createMessagesTable = 
            "CREATE TABLE " + TABLE_MESSAGES + " (" +
            COL_MSG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_MSG_CONV_ID + " INTEGER NOT NULL, " +
            COL_MSG_SENDER + " TEXT NOT NULL, " +
            COL_MSG_CONTENT + " TEXT NOT NULL, " +
            COL_MSG_TIMESTAMP + " INTEGER NOT NULL, " +
            COL_MSG_TYPE + " TEXT DEFAULT 'text', " +
            "FOREIGN KEY(" + COL_MSG_CONV_ID + ") REFERENCES " + TABLE_CONVERSATIONS + "(" + COL_CONV_ID + ") ON DELETE CASCADE" +
            ")";
        
        // Table cache IA
        String createCacheTable = 
            "CREATE TABLE " + TABLE_CACHE + " (" +
            COL_CACHE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COL_CACHE_PROMPT + " TEXT NOT NULL, " +
            COL_CACHE_RESPONSE + " TEXT NOT NULL, " +
            COL_CACHE_TIMESTAMP + " INTEGER NOT NULL" +
            ")";
        
        // Table settings
        String createSettingsTable = 
            "CREATE TABLE " + TABLE_SETTINGS + " (" +
            COL_SETTINGS_KEY + " TEXT PRIMARY KEY, " +
            COL_SETTINGS_VALUE + " TEXT NOT NULL" +
            ")";
        
        db.execSQL(createConversationsTable);
        db.execSQL(createMessagesTable);
        db.execSQL(createCacheTable);
        db.execSQL(createSettingsTable);
        
        // Index pour les performances
        db.execSQL("CREATE INDEX idx_messages_conversation ON " + TABLE_MESSAGES + "(" + COL_MSG_CONV_ID + ")");
        db.execSQL("CREATE INDEX idx_messages_timestamp ON " + TABLE_MESSAGES + "(" + COL_MSG_TIMESTAMP + ")");
        db.execSQL("CREATE INDEX idx_cache_timestamp ON " + TABLE_CACHE + "(" + COL_CACHE_TIMESTAMP + ")");
    }
    
    // ========== GESTION DES CONVERSATIONS ==========
    
    /**
     * Crée une nouvelle conversation
     */
    public long createConversation(String title) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        long timestamp = System.currentTimeMillis();
        values.put(COL_CONV_TITLE, SecurityUtils.sanitizeInput(title));
        values.put(COL_CONV_CREATED, timestamp);
        values.put(COL_CONV_UPDATED, timestamp);
        
        long conversationId = db.insert(TABLE_CONVERSATIONS, null, values);
        Log.d(TAG, "Conversation créée avec ID: " + conversationId);
        return conversationId;
    }
    
    /**
     * Récupère toutes les conversations
     */
    public List<Conversation> getAllConversations() {
        List<Conversation> conversations = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT * FROM " + TABLE_CONVERSATIONS + 
                      " ORDER BY " + COL_CONV_UPDATED + " DESC";
        
        Cursor cursor = db.rawQuery(query, null);
        
        if (cursor.moveToFirst()) {
            do {
                Conversation conv = new Conversation(
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONV_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_CONV_TITLE)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONV_CREATED)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_CONV_UPDATED))
                );
                conversations.add(conv);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        return conversations;
    }
    
    /**
     * Met à jour une conversation
     */
    public void updateConversation(long conversationId, String title) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COL_CONV_TITLE, SecurityUtils.sanitizeInput(title));
        values.put(COL_CONV_UPDATED, System.currentTimeMillis());
        
        int rows = db.update(TABLE_CONVERSATIONS, values, 
                           COL_CONV_ID + " = ?", new String[]{String.valueOf(conversationId)});
        
        Log.d(TAG, "Conversation mise à jour: " + rows + " lignes affectées");
    }
    
    /**
     * Supprime une conversation
     */
    public void deleteConversation(long conversationId) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        // Supprimer d'abord les messages (CASCADE)
        db.delete(TABLE_MESSAGES, COL_MSG_CONV_ID + " = ?", 
                 new String[]{String.valueOf(conversationId)});
        
        // Supprimer la conversation
        int rows = db.delete(TABLE_CONVERSATIONS, COL_CONV_ID + " = ?", 
                           new String[]{String.valueOf(conversationId)});
        
        Log.d(TAG, "Conversation supprimée: " + rows + " lignes affectées");
    }
    
    // ========== GESTION DES MESSAGES ==========
    
    /**
     * Ajoute un message à une conversation
     */
    public long addMessage(long conversationId, String sender, String content, String type) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COL_MSG_CONV_ID, conversationId);
        values.put(COL_MSG_SENDER, SecurityUtils.sanitizeInput(sender));
        values.put(COL_MSG_CONTENT, SecurityUtils.sanitizeInput(content));
        values.put(COL_MSG_TIMESTAMP, System.currentTimeMillis());
        values.put(COL_MSG_TYPE, type != null ? SecurityUtils.sanitizeInput(type) : "text");
        
        long messageId = db.insert(TABLE_MESSAGES, null, values);
        
        // Mettre à jour le timestamp de la conversation
        updateConversationTimestamp(conversationId);
        
        Log.d(TAG, "Message ajouté avec ID: " + messageId);
        return messageId;
    }
    
    /**
     * Récupère les messages d'une conversation
     */
    public List<Message> getMessages(long conversationId, int limit) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT * FROM " + TABLE_MESSAGES + 
                      " WHERE " + COL_MSG_CONV_ID + " = ?" +
                      " ORDER BY " + COL_MSG_TIMESTAMP + " ASC" +
                      (limit > 0 ? " LIMIT " + limit : "");
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(conversationId)});
        
        if (cursor.moveToFirst()) {
            do {
                Message msg = new Message(
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_CONV_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_SENDER)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CONTENT)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_TYPE))
                );
                messages.add(msg);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        return messages;
    }
    
    /**
     * Met à jour le timestamp d'une conversation
     */
    private void updateConversationTimestamp(long conversationId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_CONV_UPDATED, System.currentTimeMillis());
        
        db.update(TABLE_CONVERSATIONS, values, 
                 COL_CONV_ID + " = ?", new String[]{String.valueOf(conversationId)});
    }
    
    // ========== GESTION DU CACHE IA ==========
    
    /**
     * Met en cache une réponse IA
     */
    public void cacheAIResponse(String prompt, String response) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COL_CACHE_PROMPT, SecurityUtils.sanitizeInput(prompt));
        values.put(COL_CACHE_RESPONSE, SecurityUtils.sanitizeInput(response));
        values.put(COL_CACHE_TIMESTAMP, System.currentTimeMillis());
        
        db.insert(TABLE_CACHE, null, values);
        Log.d(TAG, "Réponse IA mise en cache");
    }
    
    /**
     * Récupère une réponse du cache
     */
    public String getCachedResponse(String prompt) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT " + COL_CACHE_RESPONSE + " FROM " + TABLE_CACHE + 
                      " WHERE " + COL_CACHE_PROMPT + " = ?" +
                      " ORDER BY " + COL_CACHE_TIMESTAMP + " DESC LIMIT 1";
        
        Cursor cursor = db.rawQuery(query, new String[]{SecurityUtils.sanitizeInput(prompt)});
        
        String response = null;
        if (cursor.moveToFirst()) {
            response = cursor.getString(cursor.getColumnIndexOrThrow(COL_CACHE_RESPONSE));
        }
        
        cursor.close();
        return response;
    }
    
    /**
     * Nettoie le cache ancien (plus de 7 jours)
     */
    public void cleanOldCache() {
        SQLiteDatabase db = this.getWritableDatabase();
        long sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
        
        int rows = db.delete(TABLE_CACHE, COL_CACHE_TIMESTAMP + " < ?", 
                           new String[]{String.valueOf(sevenDaysAgo)});
        
        Log.d(TAG, "Cache nettoyé: " + rows + " entrées supprimées");
    }
    
    // ========== GESTION DES PARAMÈTRES ==========
    
    /**
     * Sauvegarde un paramètre
     */
    public void saveSetting(String key, String value) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(COL_SETTINGS_KEY, SecurityUtils.sanitizeInput(key));
        values.put(COL_SETTINGS_VALUE, SecurityUtils.sanitizeInput(value));
        
        db.insertWithOnConflict(TABLE_SETTINGS, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        Log.d(TAG, "Paramètre sauvegardé: " + key);
    }
    
    /**
     * Récupère un paramètre
     */
    public String getSetting(String key, String defaultValue) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query = "SELECT " + COL_SETTINGS_VALUE + " FROM " + TABLE_SETTINGS + 
                      " WHERE " + COL_SETTINGS_KEY + " = ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{SecurityUtils.sanitizeInput(key)});
        
        String value = defaultValue;
        if (cursor.moveToFirst()) {
            value = cursor.getString(cursor.getColumnIndexOrThrow(COL_SETTINGS_VALUE));
        }
        
        cursor.close();
        return value;
    }
    
    // ========== CLASSES DE DONNÉES ==========
    
    public static class Conversation {
        public long id;
        public String title;
        public long created;
        public long updated;
        
        public Conversation(long id, String title, long created, long updated) {
            this.id = id;
            this.title = title;
            this.created = created;
            this.updated = updated;
        }
    }
    
    public static class Message {
        public long id;
        public long conversationId;
        public String sender;
        public String content;
        public long timestamp;
        public String type;
        
        public Message(long id, long conversationId, String sender, String content, 
                      long timestamp, String type) {
            this.id = id;
            this.conversationId = conversationId;
            this.sender = sender;
            this.content = content;
            this.timestamp = timestamp;
            this.type = type;
        }
    }
}
