package com.chatai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ConsoleMessage;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import android.content.Context;
import androidx.fragment.app.FragmentTransaction;
import android.Manifest;
import android.widget.Toast;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
import java.util.Locale;
import com.chatai.fragments.KittFragment;

public class MainActivity extends FragmentActivity implements com.chatai.fragments.KittFragment.KittFragmentListener {
    private WebView webView;
    private WebAppInterface webInterface;
    private static final String TAG = "MainActivity";
    
    // ⭐ NOUVEAU : Référence statique à MainActivity pour accès depuis BackgroundService
    private static MainActivity sInstance = null;
    
    // Service en arrière-plan
    private BackgroundService backgroundService;
    private boolean isServiceBound = false;
    
    // Serveurs locaux (maintenant gérés par BackgroundService)
    private HttpServer httpServer;
    private WebSocketServer webSocketServer;
    private RealtimeAIService aiService;
    private FileServer fileServer;
    private WebServer webServer;
    
    // Interface KITT (version unique refactorisée)
    private FrameLayout kittFragmentContainer;
    private FrameLayout kittDrawerContainer;
    private KittFragment kittFragment;
    private boolean isKittVisible = false;
    private boolean isKittPersistent = false;
    
    // ⭐ NOUVEAU : Service TTS global pour utilisation depuis le chat (même si KITT non visible)
    private com.chatai.managers.KittTTSManager globalTTSManager;
    
    // Permissions
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private boolean hasRequestedPermissions = false;
    
    // Speech Recognition (simplifié avec Intent standard)
    // ⭐ PUBLIC pour être accessible depuis WebAppInterface
    public static final int REQUEST_SPEECH_RECOGNITION = 2001;
    public static final int REQUEST_SPEECH_RECOGNITION_HOTWORD = 2002;  // ⭐ NOUVEAU : Pour hotword

    /**
     * ⭐ NOUVEAU : Obtenir la référence statique à MainActivity (pour accès depuis BackgroundService)
     */
    public static MainActivity getInstance() {
        return sInstance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ⭐ NOUVEAU : Enregistrer la référence statique à MainActivity
        sInstance = this;
        Log.i(TAG, "MainActivity onCreate démarré");
        setContentView(R.layout.activity_main);
        Log.i(TAG, "Layout chargé: activity_main");

        // Vérifier et demander les permissions
        if (!checkPermissions()) {
            Log.i(TAG, "Permissions manquantes, demande en cours");
            requestPermissions();
            return;
        }

        // Démarrer le service en arrière-plan
        startBackgroundService();
        
        // ⭐ NOUVEAU : Initialiser le fichier de log (créer répertoire et fichier s'ils n'existent pas)
        initializeLogFile();
        
        // ⭐ NOUVEAU : Initialiser le service TTS global pour utilisation depuis le chat
        initializeGlobalTTS();
        
        setupWebView();
        setupKittInterface();
        setupKittButton();
        
        // Vérifier si lancé depuis Quick Settings Tile
        handleKittActivationIntent(getIntent());
        
        Log.i(TAG, "MainActivity onCreate terminé");
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        Log.i(TAG, "📨 onNewIntent appelé");
        Log.i(TAG, "   activate_kitt = " + intent.getBooleanExtra("activate_kitt", false));
        
        // Gérer l'activation KITT depuis Quick Settings Tile
        handleKittActivationIntent(intent);
    }
    
    /**
     * Gère l'activation de KITT depuis la Quick Settings Tile
     */
    private void handleKittActivationIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("activate_kitt", false)) {
            Log.i(TAG, "🚗 KITT activation requested from Quick Settings Tile");
            
            // Ouvrir KITT immédiatement
            showKittInterface();
            
            // Attendre que le fragment s'initialise complètement (tous les managers)
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (kittFragment != null && kittFragment.isAdded()) {
                    try {
                        Log.i(TAG, "🎯 Activating KITT voice listening...");
                        kittFragment.activateVoiceListening();
                        Log.i(TAG, "✅ KITT voice listening activated from tile");
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Erreur activation KITT depuis tile", e);
                    }
                } else {
                    Log.e(TAG, "❌ Cannot activate voice: Fragment not ready");
                }
            }, 1000);
        }
    }

    private void setupWebView() {
        webView = findViewById(R.id.webview);
        webInterface = new WebAppInterface(this, this);
        
        // Les serveurs seront injectés après leur création

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setDomStorageEnabled(true);

        webView.addJavascriptInterface(webInterface, "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // ⭐ Logs WebView réduits - ne log que si vraiment nécessaire
                // Log.d(TAG, "Page chargée");
            }
        });
        
        // ⭐ TAIRE les logs de console JavaScript de la WebView
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                // Ignorer tous les logs de console JavaScript (console.log, console.warn, console.error)
                // Pour ne pas polluer les logs Android
                return true; // Message traité, ne pas afficher dans logcat
            }
        });

        // Différer le chargement de la WebView pour éviter de bloquer le thread principal
        // Cela permet au premier frame de s'afficher avant le chargement lourd
        webView.post(() -> {
            webView.loadUrl("file:///android_asset/webapp/index.html");
        });
    }

    private void setupKittInterface() {
        kittFragmentContainer = findViewById(R.id.kitt_fragment_container);
        kittDrawerContainer = findViewById(R.id.kitt_drawer_container);
        
        // Créer le fragment KITT (version refactorisée unique)
        kittFragment = new com.chatai.fragments.KittFragment();
        kittFragment.setFileServer(fileServer);
        kittFragment.setKittFragmentListener(this);
        
        Log.i(TAG, "✅ Interface KITT (architecture modulaire) initialisée");
    }
    
    private void setupKittButton() {
        // Le bouton KITT est maintenant intégré dans l'interface web
        // La fonctionnalité est gérée par le JavaScript
        Log.i(TAG, "Bouton KITT intégré dans l'interface web");
    }
    
    /**
     * Obtenir les permissions requises selon la version d'Android
     */
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) : READ_MEDIA permissions
            return new String[]{
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            // Android < 13 : READ/WRITE_EXTERNAL_STORAGE
            return new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            };
        }
    }
    
    /**
     * Vérifie si toutes les permissions sont accordées
     */
    private boolean checkPermissions() {
        // Si on a déjà demandé les permissions, ne plus redemander
        if (hasRequestedPermissions) {
            return true;
        }
        
        // Android 11+ : Vérifier MANAGE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                return false;
            }
        }
        
        String[] permissions = getRequiredPermissions();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Permission manquante: " + permission);
                return false;
            }
        }
        
        Log.i(TAG, "Toutes les permissions sont accordées");
        return true;
    }
    
    /**
     * Demande les permissions manquantes
     */
    private void requestPermissions() {
        if (hasRequestedPermissions) {
            // Éviter de redemander les permissions en boucle
            Log.i(TAG, "Permissions déjà demandées, initialisation de l'app");
            startBackgroundService();
            setupWebView();
            setupKittInterface();
            setupKittButton();
            return;
        }
        
        hasRequestedPermissions = true;
        Log.i(TAG, "Demande des permissions manquantes");
        
        // Demander l'accès complet au stockage pour Android 11+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Log.i(TAG, "Demande d'accès complet au stockage");
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Erreur lors de la demande MANAGE_EXTERNAL_STORAGE", e);
                }
            }
        }
        
        // Demander les permissions standards
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSION_REQUEST_CODE);
    }
    
    /**
     * Gère la réponse aux demandes de permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = grantResults.length > 0;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "Permission refusée: " + permissions[i]);
                    allGranted = false;
                } else {
                    Log.i(TAG, "Permission accordée: " + permissions[i]);
                }
            }
            
            if (allGranted) {
                Log.i(TAG, "Toutes les permissions accordées, initialisation de l'app");
            } else {
                Log.w(TAG, "Certaines permissions refusées, fonctionnalités limitées");
                Toast.makeText(this, "Certaines fonctionnalités peuvent être limitées sans permissions", Toast.LENGTH_LONG).show();
            }
            
            // Toujours initialiser l'app (ne PAS appeler recreate())
            Log.i(TAG, "Initialisation de l'app");
            startBackgroundService();
            setupWebView();
            setupKittInterface();
            setupKittButton();
        }
    }
    
    /**
     * Démarre le service en arrière-plan
     */
    private void startBackgroundService() {
        try {
            Log.i(TAG, "Démarrage du service en arrière-plan...");
            
            Intent serviceIntent = new Intent(this, BackgroundService.class);
            startForegroundService(serviceIntent);
            
            // Lier le service
            bindService(serviceIntent, serviceConnection, android.content.Context.BIND_AUTO_CREATE);
            
            Log.i(TAG, "Service en arrière-plan démarré");
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur démarrage service en arrière-plan", e);
            // Fallback vers les serveurs locaux
            startLocalServers();
        }
    }
    
    /**
     * Connection au service en arrière-plan
     */
    private android.content.ServiceConnection serviceConnection = new android.content.ServiceConnection() {
        @Override
        public void onServiceConnected(android.content.ComponentName name, android.os.IBinder service) {
            Log.i(TAG, "Service en arrière-plan connecté");
            BackgroundService.LocalBinder binder = (BackgroundService.LocalBinder) service;
            backgroundService = binder.getService();
            isServiceBound = true;
            
            // Injecter les serveurs du BackgroundService dans l'interface web
            if (backgroundService != null) {
                httpServer = backgroundService.getHttpServer();
                webSocketServer = backgroundService.getWebSocketServer();
                aiService = backgroundService.getAIService();
                fileServer = backgroundService.getFileServer();
                
                WebAppInterface.setServers(httpServer, webSocketServer, aiService, fileServer);
                Log.i(TAG, "Serveurs du BackgroundService injectés dans WebAppInterface");
            }
        }
        
        @Override
        public void onServiceDisconnected(android.content.ComponentName name) {
            Log.w(TAG, "Service en arrière-plan déconnecté");
            backgroundService = null;
            isServiceBound = false;
        }
    };
    
    /**
     * Redémarre les serveurs (utile après changement de configuration)
     */
    public void restartServers() {
        if (isServiceBound && backgroundService != null) {
            Log.i(TAG, "Redémarrage des serveurs via service en arrière-plan");
            backgroundService.restartServers();
        } else {
            Log.w(TAG, "Service non disponible, redémarrage des serveurs locaux");
            startLocalServers();
        }
    }

    /**
     * Démarre les serveurs locaux
     */
    private void startLocalServers() {
        try {
            Log.i(TAG, "Démarrage des serveurs locaux...");
            
            // Démarrer le serveur HTTP
            httpServer = new HttpServer(this);
            httpServer.start();
            Log.i(TAG, "Serveur HTTP démarré sur le port " + httpServer.getPort());
            
            // Démarrer le serveur WebSocket
            webSocketServer = new WebSocketServer(this);
            webSocketServer.start();
            Log.i(TAG, "Serveur WebSocket démarré");
            
            // Démarrer le service IA
            aiService = new RealtimeAIService(this, httpServer, webSocketServer);
            Log.i(TAG, "Service IA temps réel initialisé");
            
            // Démarrer le serveur de fichiers
            fileServer = new FileServer(this);
            fileServer.start();
            Log.i(TAG, "Serveur de fichiers démarré sur le port " + fileServer.getPort());
            
            // Démarrer le serveur web pour les assets
            webServer = new WebServer(this);
            webServer.start();
            Log.i(TAG, "Serveur web démarré sur le port 8888");
            
            // Lier le FileServer au HttpServer
            httpServer.setFileServer(fileServer);
            
            // Injecter les serveurs dans l'interface web
            WebAppInterface.setServers(httpServer, webSocketServer, aiService, fileServer);
            
            // ⭐ NOUVEAU: Initialiser VisionService pour analyse d'images
            if (webInterface != null) {
                webInterface.initializeVisionService();
                Log.i(TAG, "VisionService initialisé");
            }
            
            Log.i(TAG, "Tous les serveurs sont opérationnels");
            
        } catch (Exception e) {
            Log.e(TAG, "Erreur démarrage serveurs: ", e);
            Toast.makeText(this, "Erreur démarrage serveurs", Toast.LENGTH_SHORT).show();
        }
    }

    public void openKittInterface() {
        try {
            if (!isKittVisible) {
                // Afficher l'interface KITT
                showKittInterface();
            } else {
                // Masquer l'interface KITT
                hideKittInterface();
            }
            Log.i(TAG, "Interface KITT " + (isKittVisible ? "affichée" : "masquée"));
        } catch (Exception e) {
            Log.e(TAG, "Erreur lancement KITT: ", e);
            Toast.makeText(this, "Erreur lancement KITT", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showKittInterface() {
        if (kittFragmentContainer == null || kittFragment == null) {
            Log.e(TAG, "❌ KITT container or fragment is null");
            return;
        }
        
        // Masquer le WebView
        webView.setVisibility(View.GONE);
        
        // Afficher le container KITT
        kittFragmentContainer.setVisibility(View.VISIBLE);
        
        // Ajouter le fragment KITT si pas déjà ajouté
        if (!kittFragment.isAdded()) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.kitt_fragment_container, kittFragment, "kitt_fragment");
            transaction.commit();
            Log.i(TAG, "✅ KittFragment added to container");
        }
        
        isKittVisible = true;
        Log.i(TAG, "Interface KITT affichée");
    }
    
    public void hideKittInterface() {
        if (kittFragmentContainer != null) {
            // Masquer le container KITT
            kittFragmentContainer.setVisibility(View.GONE);
            
            // Afficher le WebView
            webView.setVisibility(View.VISIBLE);
            
            isKittVisible = false;
            Log.i(TAG, "Interface KITT masquée");
        }
    }

    @Override
    public void onBackPressed() {
        if (isKittVisible && !isKittPersistent) {
            // Si KITT est visible et pas en mode persistant, le masquer
            hideKittInterface();
        } else if (isKittVisible && isKittPersistent) {
            // En mode persistant, ne pas permettre de fermer KITT avec le bouton retour
            Toast.makeText(this, "Mode KITT persistant actif - Utilisez le bouton PERSIST pour désactiver", Toast.LENGTH_SHORT).show();
        } else if (webView.canGoBack()) {
            // Si on peut revenir en arrière dans le WebView
            webView.goBack();
        } else {
            // Sinon, fermer l'application
            super.onBackPressed();
        }
    }
    
    public void setKittPersistentMode(boolean persistent) {
        isKittPersistent = persistent;
        Log.i(TAG, "Mode KITT persistant: " + (persistent ? "activé" : "désactivé"));
    }
    
    public void toggleKittPersistentMode() {
        setKittPersistentMode(!isKittPersistent);
    }
    
    /**
     * ⭐ NOUVEAU : Initialiser le service TTS global pour utilisation depuis le chat
     * Ce TTS fonctionne même si KITT n'est pas visible
     */
    /**
     * Initialise le fichier de log (créer répertoire et fichier s'ils n'existent pas)
     * Garantit que le fichier existe pour les diagnostics
     */
    private void initializeLogFile() {
        try {
            com.chatai.database.DiagnosticsHelper diagnosticsHelper = 
                new com.chatai.database.DiagnosticsHelper(this);
            boolean success = diagnosticsHelper.initializeLogFile();
            if (success) {
                Log.d(TAG, "Fichier de log initialisé avec succès");
            } else {
                Log.w(TAG, "Échec de l'initialisation du fichier de log");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de l'initialisation du fichier de log", e);
        }
    }

    private void initializeGlobalTTS() {
        try {
            // Créer un listener minimal pour le TTS global (seulement pour logging)
            com.chatai.managers.KittTTSManager.TTSListener ttsListener = new com.chatai.managers.KittTTSManager.TTSListener() {
                @Override
                public void onTTSReady() {
                    Log.i(TAG, "✅ TTS global prêt");
                }
                
                @Override
                public void onTTSStart(String utteranceId) {
                    Log.d(TAG, "🔊 TTS global: début parole (utteranceId: " + utteranceId + ")");
                }
                
                @Override
                public void onTTSDone(String utteranceId) {
                    Log.d(TAG, "✅ TTS global: fin parole (utteranceId: " + utteranceId + ")");
                }
                
                @Override
                public void onTTSError(String utteranceId) {
                    Log.e(TAG, "❌ TTS global: erreur (utteranceId: " + utteranceId + ")");
                }
            };
            
            // Créer et initialiser le TTS manager global
            globalTTSManager = new com.chatai.managers.KittTTSManager(this, ttsListener);
            globalTTSManager.initialize();
            
            // Sélectionner la personnalité par défaut (KITT) - doit être final pour lambda
            android.content.SharedPreferences prefs = getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE);
            final String personality = prefs.getString("selected_personality", "KITT") != null ? 
                prefs.getString("selected_personality", "KITT") : "KITT";
            
            // Sélectionner la voix après initialisation (via handler pour s'assurer que TTS est prêt)
            // Utiliser une référence finale pour le TTS manager
            final com.chatai.managers.KittTTSManager finalTTSManager = globalTTSManager;
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (finalTTSManager != null && finalTTSManager.isTTSReady()) {
                    finalTTSManager.selectVoiceForPersonality(personality);
                    Log.i(TAG, "✅ TTS global configuré avec personnalité: " + personality);
                }
            }, 500); // Délai pour s'assurer que TTS est initialisé
            
            Log.i(TAG, "✅ Service TTS global initialisé");
        } catch (Exception e) {
            Log.e(TAG, "❌ Erreur initialisation TTS global", e);
        }
    }
    
    /**
     * ⭐ NOUVEAU : Obtenir le service TTS global (pour WebAppInterface)
     */
    public com.chatai.managers.KittTTSManager getGlobalTTSManager() {
        return globalTTSManager;
    }
    
    @Override
    protected void onDestroy() {
        // ⭐ FIX : Nettoyer le WebView AVANT super.onDestroy() pour éviter les erreurs WindowManager
        if (webView != null) {
            try {
                webView.onPause();
                webView.clearHistory();
                webView.clearCache(true);
                webView.loadUrl("about:blank");
                webView.removeAllViews();
                webView.destroyDrawingCache();
                // ⭐ FIX : Détacher le WebView de son parent avant destruction
                if (webView.getParent() != null) {
                    ((android.view.ViewGroup) webView.getParent()).removeView(webView);
                }
                webView = null;
                Log.i(TAG, "✅ WebView nettoyé");
            } catch (Exception e) {
                Log.w(TAG, "Erreur nettoyage WebView", e);
            }
        }
        
        // ⭐ NOUVEAU : Détruire le TTS global
        if (globalTTSManager != null) {
            try {
                globalTTSManager.destroy();
                globalTTSManager = null;
                Log.i(TAG, "✅ TTS global détruit");
            } catch (Exception e) {
                Log.e(TAG, "Erreur destruction TTS global", e);
            }
        }
        
        // ⭐ NOUVEAU : Réinitialiser la référence statique à MainActivity
        if (sInstance == this) {
            sInstance = null;
        }
        
        // Délier le service en arrière-plan
        if (isServiceBound) {
            try {
                unbindService(serviceConnection);
                isServiceBound = false;
            } catch (Exception e) {
                Log.w(TAG, "Erreur déliaison service", e);
            }
        }
        
        // ⭐ FIX : Appeler super.onDestroy() APRÈS le nettoyage pour éviter les erreurs WindowManager
        super.onDestroy();
        
        // Note: Les serveurs continuent de fonctionner via BackgroundService
        // Ils ne s'arrêtent que si l'app est complètement fermée
        Log.i(TAG, "MainActivity détruite - serveurs maintenus par BackgroundService");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Le service en arrière-plan maintient les serveurs actifs
        Log.i(TAG, "App en pause - serveurs maintenus par BackgroundService");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Vérifier que les serveurs sont toujours actifs
        if (isServiceBound && backgroundService != null) {
            boolean serversRunning = backgroundService.areServersRunning();
            Log.i(TAG, "Serveurs actifs: " + serversRunning);
        }
    }
    
    /**
     * ⭐ SIMPLIFICATION Google Speech : Utilise Intent standard au lieu de SpeechRecognizer manuel
     * Reçoit le résultat de RecognizerIntent et l'insère dans le textInput de la webapp
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SPEECH_RECOGNITION || requestCode == REQUEST_SPEECH_RECOGNITION_HOTWORD) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (results != null && !results.isEmpty()) {
                    String spokenText = results.get(0);
                    Log.i(TAG, "Speech Recognition result: " + spokenText + " (requestCode=" + requestCode + ")");
                    
                    // ⭐ NOUVEAU : Si c'est pour le hotword, envoyer le résultat à BackgroundService
                    if (requestCode == REQUEST_SPEECH_RECOGNITION_HOTWORD) {
                        // Envoyer le résultat au BackgroundService pour traitement hotword
                        Intent intent = new Intent(this, com.chatai.BackgroundService.class);
                        intent.setAction(com.chatai.BackgroundService.ACTION_HOTWORD_SPEECH_RESULT);
                        intent.putExtra("transcription", spokenText);
                        startService(intent);
                        Log.i(TAG, "✅ Transcription hotword envoyée à BackgroundService: " + spokenText);
                    } else {
                        // Comportement normal : insérer le texte dans le textInput de la webapp
                        if (webView != null) {
                            // Échapper les guillemets simples et sauts de ligne pour JavaScript
                            String safeText = spokenText.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
                            String jsCode = 
                                "var input = document.getElementById('messageInput'); " +
                                "if (input) { " +
                                "  input.value = '" + safeText + "'; " +
                                "  if (input.dispatchEvent) { " +
                                "    input.dispatchEvent(new Event('input', { bubbles: true })); " +
                                "  } " +
                                "  if (window.secureChatApp && window.secureChatApp.chatUI && window.secureChatApp.chatUI.adjustTextareaHeight) { " +
                                "    window.secureChatApp.chatUI.adjustTextareaHeight(); " +
                                "  } " +
                                "}";
                            webView.evaluateJavascript(jsCode, null);
                            Log.i(TAG, "✅ Text inserted into messageInput");
                        }
                    }
                }
            } else if (resultCode == RESULT_CANCELED) {
                Log.i(TAG, "Speech Recognition cancelled by user (requestCode=" + requestCode + ")");
                // ⭐ NOUVEAU : Si c'est pour le hotword, notifier BackgroundService
                if (requestCode == REQUEST_SPEECH_RECOGNITION_HOTWORD) {
                    Intent intent = new Intent(this, com.chatai.BackgroundService.class);
                    intent.setAction(com.chatai.BackgroundService.ACTION_HOTWORD_SPEECH_CANCELED);
                    startService(intent);
                    Log.i(TAG, "✅ Annulation hotword notifiée à BackgroundService");
                }
            } else {
                Log.w(TAG, "Speech Recognition failed (resultCode=" + resultCode + ", requestCode=" + requestCode + ")");
                // ⭐ NOUVEAU : Si c'est pour le hotword, notifier BackgroundService
                if (requestCode == REQUEST_SPEECH_RECOGNITION_HOTWORD) {
                    Intent intent = new Intent(this, com.chatai.BackgroundService.class);
                    intent.setAction(com.chatai.BackgroundService.ACTION_HOTWORD_SPEECH_ERROR);
                    intent.putExtra("error", "Speech Recognition failed (resultCode=" + resultCode + ")");
                    startService(intent);
                    Log.i(TAG, "✅ Erreur hotword notifiée à BackgroundService");
                }
            }
        }
    }
    
    /**
     * Obtient la WebView pour l'exécution de JavaScript
     */
    public WebView getWebView() {
        return webView;
    }
}
