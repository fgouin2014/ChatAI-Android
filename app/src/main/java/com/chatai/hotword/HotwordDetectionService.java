package com.chatai.hotword;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;

/**
 * Service de détection de hotword utilisant Porcupine
 * 
 * Détecte "Kit-Kat" en français via Porcupine SDK
 * 
 * IMPORTANT: Ce service est INDÉPENDANT de KITT et de l'IA
 * Il ne fait QUE détecter le hotword et notifier via callback
 */
public class HotwordDetectionService implements HotwordDetector {
    private static final String TAG = "HotwordDetection";
    
    private final Context context;
    private final HotwordPreferences prefs;
    private final Handler mainHandler;
    
    private AudioRecord audioRecord;
    private Thread detectionThread;
    private boolean isRunning = false;
    private boolean isPaused = false;
    
    // Porcupine SDK instance
    private Porcupine porcupine;
    
    // Callback pour notifier la détection
    public interface HotwordCallback {
        void onHotwordDetected(String keyword);
    }
    
    private HotwordCallback callback;
    
    // Audio configuration
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int FRAME_LENGTH = 512; // Porcupine frame length
    
    public HotwordDetectionService(Context context) {
        this.context = context;
        this.prefs = new HotwordPreferences(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    @Override
    public void setCallback(HotwordCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Initialise Porcupine avec les fichiers de modèle
     */
    @Override
    public boolean initialize() {
        if (!prefs.areFilesReady()) {
            Log.e(TAG, "Cannot initialize: missing files or API key");
            return false;
        }
        
        try {
            String modelPath = prefs.getPorcupineModelPath();
            String keywordPath = prefs.getKeywordPath();
            String apiKey = prefs.getPorcupineApiKey();
            float sensitivity = prefs.getSensitivity();
            
            Log.i(TAG, "Initializing Porcupine...");
            Log.i(TAG, "  Model: " + modelPath);
            Log.i(TAG, "  Keyword: " + keywordPath);
            Log.i(TAG, "  Sensitivity: " + sensitivity);
            
            // Initialiser Porcupine avec SDK Android
            porcupine = new Porcupine.Builder()
                .setAccessKey(apiKey)
                .setModelPath(modelPath)
                .setKeywordPaths(new String[]{keywordPath})
                .setSensitivities(new float[]{sensitivity})
                .build(context);
            
            Log.i(TAG, "Porcupine initialized successfully");
            return true;
            
        } catch (PorcupineException e) {
            Log.e(TAG, "Error initializing Porcupine: " + e.getMessage(), e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error initializing Porcupine: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Démarre la détection de hotword
     */
    @Override
    public void start() {
        if (isRunning) {
            Log.w(TAG, "Hotword detection already running");
            return;
        }
        
        if (porcupine == null) {
            Log.e(TAG, "Cannot start: Porcupine not initialized");
            return;
        }
        
        try {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            if (bufferSize == AudioRecord.ERROR_BAD_VALUE || bufferSize == AudioRecord.ERROR) {
                Log.e(TAG, "Invalid audio buffer size");
                return;
            }
            
            audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            );
            
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord not initialized");
                audioRecord.release();
                audioRecord = null;
                return;
            }
            
            audioRecord.startRecording();
            isRunning = true;
            isPaused = false;
            
            detectionThread = new Thread(this::detectionLoop, "HotwordDetection");
            detectionThread.start();
            
            Log.i(TAG, "Hotword detection started");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting hotword detection: " + e.getMessage(), e);
            isRunning = false;
        }
    }
    
    /**
     * Arrête la détection
     */
    @Override
    public void stop() {
        if (!isRunning) {
            return;
        }
        
        isRunning = false;
        isPaused = false;
        
        if (audioRecord != null) {
            try {
                audioRecord.stop();
                audioRecord.release();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping AudioRecord: " + e.getMessage());
            }
            audioRecord = null;
        }
        
        if (detectionThread != null) {
            try {
                detectionThread.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while stopping thread");
            }
            detectionThread = null;
        }
        
        Log.i(TAG, "Hotword detection stopped");
    }
    
    /**
     * Met en pause (garde les ressources mais n'écoute pas)
     */
    @Override
    public void pause() {
        isPaused = true;
        Log.d(TAG, "Hotword detection paused");
    }
    
    /**
     * Reprend la détection
     */
    @Override
    public void resume() {
        isPaused = false;
        Log.d(TAG, "Hotword detection resumed");
    }
    
    /**
     * Boucle de détection principale
     */
    private void detectionLoop() {
        short[] audioBuffer = new short[FRAME_LENGTH];
        
        while (isRunning && audioRecord != null) {
            try {
                if (isPaused) {
                    Thread.sleep(100);
                    continue;
                }
                
                int samplesRead = audioRecord.read(audioBuffer, 0, FRAME_LENGTH);
                
                if (samplesRead < 0) {
                    Log.w(TAG, "AudioRecord read error: " + samplesRead);
                    break;
                }
                
                if (samplesRead == FRAME_LENGTH) {
                    try {
                        // Détecter avec Porcupine SDK
                        int keywordIndex = porcupine.process(audioBuffer);
                        
                    if (keywordIndex >= 0) {
                        Log.i(TAG, "Hotword detected! (keyword index: " + keywordIndex + ")");
                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onHotwordDetected("kit_kat");
                            }
                        });
                        }
                    } catch (PorcupineException e) {
                        Log.e(TAG, "Error processing audio frame: " + e.getMessage());
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error in detection loop: " + e.getMessage(), e);
                break;
            }
        }
        
        Log.d(TAG, "Detection loop ended");
    }
    
    /**
     * Libère les ressources Porcupine
     */
    @Override
    public void release() {
        stop();
        
        if (porcupine != null) {
            try {
                porcupine.delete();
                porcupine = null;
                Log.i(TAG, "Porcupine released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing Porcupine: " + e.getMessage());
            }
        }
    }
    
    @Override
    public boolean isRunning() {
        return isRunning;
    }
    
}
