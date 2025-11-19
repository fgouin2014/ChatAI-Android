package com.chatai.hotword;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * Manager pour coordonner la détection de hotword Porcupine
 * 
 * Gère le cycle de vie du HotwordDetectionService et les callbacks
 */
public class HotwordDetectionManager {
    private static final String TAG = "HotwordService";
    
    private final Context context;
    private HotwordDetector detectionService;
    private HotwordPreferences prefs;
    
    public enum State {
        STOPPED,
        STARTING,
        RUNNING,
        PAUSED,
        ERROR
    }
    
    private State currentState = State.STOPPED;
    
    public interface StateListener {
        void onStateChanged(State newState);
    }
    
    private StateListener stateListener;
    private HotwordActionRouter actionRouter;
    
    public HotwordDetectionManager(Context context) {
        this.context = context;
        this.prefs = new HotwordPreferences(context);
        this.actionRouter = new HotwordActionRouter(context);
    }
    
    public void setStateListener(StateListener listener) {
        this.stateListener = listener;
    }
    
    /**
     * Démarre le service de détection
     */
    public void start() {
        if (currentState == State.RUNNING || currentState == State.STARTING) {
            Log.w(TAG, "Hotword service already running or starting");
            return;
        }
        
        Log.i(TAG, "Hotword service start requested");
        setState(State.STARTING);
        
        if (!prefs.areFilesReady()) {
            Log.e(TAG, "Cannot start: missing hotword resources");
            setState(State.ERROR);
            showToast("Hotword: Configuration incomplète");
            return;
        }
        
        try {
            String engine = prefs.getHotwordEngine();
            if ("openwakeword".equalsIgnoreCase(engine)) {
                detectionService = new OpenWakeWordDetectionService(context, prefs);
            } else {
                // Porcupine sélectionné
                detectionService = new HotwordDetectionService(context);
                // Si Porcupine n'est pas prêt (clé bloquée/fichiers manquants), basculer automatiquement vers OWW
                if (!prefs.arePorcupineFilesReady()) {
                    Log.w(TAG, "Porcupine not ready (API key/files). Trying OpenWakeWord fallback...");
                    HotwordDetector oww = new OpenWakeWordDetectionService(context, prefs);
                    if (prefs.areOpenWakeWordFilesReady() && oww.initialize()) {
                        detectionService = oww;
                        engine = "openwakeword";
                        showToast("Hotword: Fallback vers OpenWakeWord (clé Porcupine indisponible)");
                    } else {
                        Log.e(TAG, "Fallback OpenWakeWord indisponible");
                    }
                }
            }

            detectionService.setCallback(keyword -> {
                Log.i(TAG, "Hotword detected - " + keyword);
                onHotwordDetected(keyword);
            try {
                if (actionRouter != null) {
                    actionRouter.route(keyword);
                }
            } catch (Exception e) {
                Log.e(TAG, "Router error for keyword: " + keyword, e);
            }
            });

            if (!detectionService.initialize()) {
                Log.e(TAG, "Failed to initialize hotword engine: " + engine);
                setState(State.ERROR);
                showToast("Hotword: Initialisation impossible (" + engine + ")");
                return;
            }
            
            detectionService.start();
            setState(State.RUNNING);
            Log.i(TAG, "Hotword detection started");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting hotword service: " + e.getMessage(), e);
            setState(State.ERROR);
            showToast("Hotword: Erreur - " + e.getMessage());
        }
    }
    
    /**
     * Arrête le service
     */
    public void stop() {
        if (currentState == State.STOPPED) {
            return;
        }
        
        Log.i(TAG, "Stopping hotword service");
        
        if (detectionService != null) {
            detectionService.release();
            detectionService = null;
        }
        
        setState(State.STOPPED);
    }
    
    /**
     * Met en pause
     */
    public void pause() {
        if (detectionService != null && currentState == State.RUNNING) {
            detectionService.pause();
            setState(State.PAUSED);
        }
    }
    
    /**
     * Reprend
     */
    public void resume() {
        if (detectionService != null && currentState == State.PAUSED) {
            detectionService.resume();
            setState(State.RUNNING);
        }
    }
    
    /**
     * Callback quand hotword détecté
     */
    private void onHotwordDetected(String keyword) {
        Log.i(TAG, "🔥 HOTWORD DETECTED - " + keyword);
        // Bref beep pour indiquer que l'app écoute la question
        try {
            android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 60);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120);
            // Libérer la ressource après un court délai pour éviter les timeouts
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.postDelayed(() -> {
                try {
                    tg.release();
                } catch (Throwable ignored) {}
            }, 150); // Libérer après 150ms (le beep dure 120ms)
        } catch (Throwable ignored) {}

        // ICI: Tu peux ajouter l'action à effectuer
        // Par exemple: ouvrir KITT, activer l'écoute vocale, etc.
        // MAIS: L'utilisateur a dit que le hotword ne doit PAS être attaché à KITT/IA
        // Donc on ne fait rien pour l'instant, juste logger
        
        showToast("Détection: " + keyword);
    }
    
    /**
     * Met à jour l'état et notifie le listener
     */
    private void setState(State newState) {
        if (currentState != newState) {
            State oldState = currentState;
            currentState = newState;
            Log.i(TAG, "Hotword state changed: " + oldState + " -> " + newState);
            
            if (stateListener != null) {
                stateListener.onStateChanged(newState);
            }
        }
    }
    
    public State getState() {
        return currentState;
    }
    
    public boolean isRunning() {
        return currentState == State.RUNNING;
    }
    
    private void showToast(String message) {
        // Toast sur le thread principal
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        });
    }
}
