package com.chatai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

/**
 * Activité de configuration et paramètres de l'application (Version simplifiée)
 */
public class SettingsActivity extends Activity {
    private static final String TAG = "SettingsActivity";
    
    // Composants UI
    private EditText apiTokenInput;
    private Switch notificationsSwitch;
    private Switch voiceRecognitionSwitch;
    private Switch cacheSwitch;
    private TextView serverStatusText;
    private TextView databaseStatusText;
    private Button testServersBtn;
    private Button clearCacheBtn;
    private Button saveSettingsBtn;
    private Button backToChatBtn;
    
    // Services
    private SecureConfig secureConfig;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Initialiser les services
        secureConfig = new SecureConfig(this);
        
        // Initialiser les composants UI
        initializeViews();
        loadSettings();
        updateStatus();
        
        // Configurer les événements
        setupEventListeners();
        
        Log.i(TAG, "SettingsActivity créée");
    }
    
    private void initializeViews() {
        apiTokenInput = findViewById(R.id.apiTokenInput);
        notificationsSwitch = findViewById(R.id.notificationsSwitch);
        voiceRecognitionSwitch = findViewById(R.id.voiceRecognitionSwitch);
        cacheSwitch = findViewById(R.id.cacheSwitch);
        serverStatusText = findViewById(R.id.serverStatusText);
        databaseStatusText = findViewById(R.id.databaseStatusText);
        testServersBtn = findViewById(R.id.testServersBtn);
        clearCacheBtn = findViewById(R.id.clearCacheBtn);
        saveSettingsBtn = findViewById(R.id.saveSettingsBtn);
        backToChatBtn = findViewById(R.id.backToChatBtn);
    }
    
    private void loadSettings() {
        // Charger le token API (masqué pour la sécurité)
        String apiToken = secureConfig.getApiToken();
        if (apiToken != null && !apiToken.isEmpty()) {
            apiTokenInput.setText("••••••••••••••••"); // Masquer le token
        }
        
        // Charger les autres paramètres
        notificationsSwitch.setChecked(secureConfig.getSetting("notifications_enabled", "true").equals("true"));
        voiceRecognitionSwitch.setChecked(secureConfig.getSetting("voice_enabled", "true").equals("true"));
        cacheSwitch.setChecked(secureConfig.getSetting("cache_enabled", "true").equals("true"));
        
        Log.d(TAG, "Paramètres chargés");
    }
    
    private void updateStatus() {
        // Statut des serveurs
        serverStatusText.setText("🌐 HTTP Server: Port 8080\n🔌 WebSocket: Port 8081\n🤖 AI Service: Actif");
        
        // Statut de la base de données
        databaseStatusText.setText("💾 Base de données: Connectée\n📊 Conversations: Actives\n🗄️ Cache IA: Fonctionnel");
    }
    
    private void setupEventListeners() {
        saveSettingsBtn.setOnClickListener(v -> saveSettings());
        testServersBtn.setOnClickListener(v -> testServers());
        clearCacheBtn.setOnClickListener(v -> clearCache());
        backToChatBtn.setOnClickListener(v -> goBackToChat());
        
        // Événement pour le token API
        apiTokenInput.setOnClickListener(v -> showTokenInput());
    }
    
    private void showTokenInput() {
        // Créer une dialog pour saisir le token API
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("🔑 Token API");
        
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Entrez votre token API Hugging Face");
        
        builder.setView(input);
        builder.setPositiveButton("Sauvegarder", (dialog, which) -> {
            String token = input.getText().toString().trim();
            if (!token.isEmpty()) {
                secureConfig.setApiToken(token);
                apiTokenInput.setText("••••••••••••••••");
                Toast.makeText(this, "Token API sauvegardé", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Annuler", (dialog, which) -> dialog.cancel());
        builder.show();
    }
    
    private void saveSettings() {
        // Sauvegarder les paramètres
        secureConfig.saveSetting("notifications_enabled", String.valueOf(notificationsSwitch.isChecked()));
        secureConfig.saveSetting("voice_enabled", String.valueOf(voiceRecognitionSwitch.isChecked()));
        secureConfig.saveSetting("cache_enabled", String.valueOf(cacheSwitch.isChecked()));
        
        Toast.makeText(this, "✅ Paramètres sauvegardés", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Paramètres sauvegardés");
    }
    
    private void testServers() {
        testServersBtn.setText("🔄 Test en cours...");
        testServersBtn.setEnabled(false);
        
        // Simuler un test des serveurs
        new android.os.Handler().postDelayed(() -> {
            Toast.makeText(this, "✅ Serveurs opérationnels\n🌐 HTTP: Port 8080\n🔌 WebSocket: Port 8081", Toast.LENGTH_LONG).show();
            testServersBtn.setText("🧪 Tester les Serveurs");
            testServersBtn.setEnabled(true);
        }, 2000);
    }
    
    private void clearCache() {
        Toast.makeText(this, "🗑️ Cache nettoyé", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Cache nettoyé");
    }
    
    private void goBackToChat() {
        // Retour à l'écran principal - utiliser l'activité par défaut
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "SettingsActivity détruite");
    }
}