package com.chatai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import android.graphics.Color;

/**
 * Activité de gestion de la base de données et des conversations (Version simplifiée)
 */
public class DatabaseActivity extends Activity {
    private static final String TAG = "DatabaseActivity";
    
    // Composants UI
    private LinearLayout conversationsContainer;
    private TextView databaseStatsText;
    private Button refreshBtn;
    private Button clearAllBtn;
    private Button exportBtn;
    private Button backToChatBtn;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_database);
        
        // Initialiser les composants UI
        initializeViews();
        loadConversations();
        updateStats();
        
        // Configurer les événements
        setupEventListeners();
        
        Log.i(TAG, "DatabaseActivity créée");
    }
    
    private void initializeViews() {
        conversationsContainer = findViewById(R.id.conversationsContainer);
        databaseStatsText = findViewById(R.id.databaseStatsText);
        refreshBtn = findViewById(R.id.refreshBtn);
        clearAllBtn = findViewById(R.id.clearAllBtn);
        exportBtn = findViewById(R.id.exportBtn);
        backToChatBtn = findViewById(R.id.backToChatBtn);
    }
    
    private void loadConversations() {
        conversationsContainer.removeAllViews();
        
        // Version simplifiée - afficher un message
        TextView noConversationsText = new TextView(this);
        noConversationsText.setText("📭 Fonctionnalité de base de données\n🚧 En développement\n\nCette fonctionnalité permettra de :\n• Voir toutes les conversations\n• Gérer l'historique des chats\n• Exporter les données\n• Nettoyer le cache");
        noConversationsText.setTextSize(16);
        noConversationsText.setTextColor(Color.GRAY);
        noConversationsText.setPadding(20, 20, 20, 20);
        conversationsContainer.addView(noConversationsText);
        
        Log.d(TAG, "Interface de base de données chargée");
    }
    
    private void updateStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("📊 Statistiques de la base de données:\n\n");
        stats.append("💬 Conversations: En développement\n");
        stats.append("💭 Messages: En développement\n");
        stats.append("💾 Cache IA: Actif\n");
        stats.append("🔒 Sécurité: AES-256");
        
        databaseStatsText.setText(stats.toString());
    }
    
    private void setupEventListeners() {
        refreshBtn.setOnClickListener(v -> {
            loadConversations();
            updateStats();
            Toast.makeText(this, "🔄 Données actualisées", Toast.LENGTH_SHORT).show();
        });
        
        clearAllBtn.setOnClickListener(v -> {
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("🗑️ Effacer toutes les conversations");
            builder.setMessage("🚧 Fonctionnalité en développement");
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            builder.show();
        });
        
        exportBtn.setOnClickListener(v -> {
            Toast.makeText(this, "📤 Export des conversations\n🚧 Fonctionnalité en développement", Toast.LENGTH_LONG).show();
        });
        
        backToChatBtn.setOnClickListener(v -> {
            // Retour à l'écran principal - utiliser l'activité par défaut
            finish();
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "DatabaseActivity détruite");
    }
}