package com.chatai.hotword;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class OpenWakeWordDetectionService implements HotwordDetector {

    private static final String TAG = "OpenWakeWordService";
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = 2048;
    private static final long DEFAULT_DEBOUNCE_MS = 750;

    private final Context context;
    private final HotwordPreferences prefs;
    private final Handler mainHandler;

    private final List<WakeWordModel> models = new ArrayList<>();
    private HotwordDetectionService.HotwordCallback callback;

    private AudioRecord audioRecord;
    private Thread detectionThread;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private long lastScoreLogMs = 0;
    private static final long SCORE_LOG_INTERVAL_MS = 1000;

    public OpenWakeWordDetectionService(Context context, HotwordPreferences prefs) {
        this.context = context;
        this.prefs = prefs;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void setCallback(HotwordDetectionService.HotwordCallback callback) {
        this.callback = callback;
    }

    @Override
    public boolean initialize() {
        try {
            List<HotwordPreferences.ModelConfig> configs = prefs.getOpenWakeWordModels();
            if (configs.isEmpty()) {
                throw new IllegalStateException("Aucun modèle openWakeWord configuré");
            }

            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);

            for (HotwordPreferences.ModelConfig config : configs) {
                // Profil de prétraitement dépendant du modèle
                OpenWakeWordPreprocessor.Profile profile;
                if (config.name != null && config.name.toLowerCase(java.util.Locale.ROOT).contains("hey_kitt")) {
                    // Revenir au profil qui déclenchait: fmax 4k, sans pre-emphasis, sans CMVN, échelle (log/10+2)
                    profile = new OpenWakeWordPreprocessor.Profile(4000f, false, false, true);
                } else {
                    // Profil par défaut: fmax 8k, pre-emphasis, CMVN, log brut
                    profile = OpenWakeWordPreprocessor.Profile.defaultProfile();
                }
                OpenWakeWordPreprocessor preproc = new OpenWakeWordPreprocessor(context, profile);

                File modelFile = HotwordModelLoader.copyAssetToFiles(context, config.assetPath, "hotwords/openwakeword");
                java.nio.MappedByteBuffer mapped = HotwordModelLoader.mapFileToMemory(modelFile);
                Interpreter interpreter = new Interpreter(mapped, options);
                int[] inputShape = interpreter.getInputTensor(0).shape();
                int requiredFrames = inputShape.length >= 2 ? inputShape[1] : 16;
                models.add(new WakeWordModel(config.name, interpreter, requiredFrames, config.threshold, preproc));
                Log.i(TAG, "Model loaded: " + config.name + " (" + requiredFrames + " frames, threshold " + config.threshold + ")"
                        + " preproc={fmax=" + profile.fMaxHz + ", preEmp=" + profile.usePreEmphasis
                        + ", cmvn=" + profile.useCmvn + ", div10+2=" + profile.useLogDiv10Plus2 + "}");
            }

            int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
            int targetBuffer = Math.max(minBuffer, BUFFER_SIZE);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    targetBuffer);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord init failed");
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize OpenWakeWord service", e);
            release();
            return false;
        }
    }

    @Override
    public void start() {
        if (isRunning) {
            Log.w(TAG, "OpenWakeWord already running");
            return;
        }

            if (audioRecord == null || models.isEmpty()) {
            Log.e(TAG, "Cannot start - detector not initialized");
            return;
        }

        isRunning = true;
        isPaused = false;
        audioRecord.startRecording();

        detectionThread = new Thread(this::runDetectionLoop, "oww-detection");
        detectionThread.start();
    }

    @Override
    public void stop() {
        isRunning = false;
        if (detectionThread != null) {
            try {
                detectionThread.join(500);
            } catch (InterruptedException ignored) {
            }
            detectionThread = null;
        }
        if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop();
        }
    }

    @Override
    public void pause() {
        isPaused = true;
    }

    @Override
    public void resume() {
        isPaused = false;
    }

    @Override
    public void release() {
        stop();
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        for (WakeWordModel model : models) {
            model.close();
        }
        models.clear();
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    private void runDetectionLoop() {
        short[] buffer = new short[BUFFER_SIZE];
        while (isRunning) {
            if (isPaused) {
                SystemClock.sleep(100);
                continue;
            }

            int read = audioRecord.read(buffer, 0, buffer.length);
            if (read <= 0) {
                continue;
            }

            for (WakeWordModel model : models) {
                model.preprocessor.pushSamples(buffer, read);
                float[][] featureWindow = model.preprocessor.getFeatureWindow(model.requiredFrames);
                if (featureWindow == null) {
                    continue;
                }
                float score = model.run(featureWindow);
                model.updateScoreHistory(score);
                long now = SystemClock.elapsedRealtime();
                if (now - lastScoreLogMs >= SCORE_LOG_INTERVAL_MS) {
                    if (model.name.toLowerCase(java.util.Locale.ROOT).contains("hey_kitt")) {
                        Log.d(TAG, "score " + model.name + " = " + String.format(java.util.Locale.US, "%.3f", score)
                                + " avg=" + String.format(java.util.Locale.US, "%.3f", model.getAverageScore())
                                + " effThr=" + String.format(java.util.Locale.US, "%.2f", model.getEffectiveThreshold()));
                    } else {
                        Log.d(TAG, "score " + model.name + " = " + String.format(java.util.Locale.US, "%.3f", score)
                                + " (thr " + String.format(java.util.Locale.US, "%.2f", model.threshold) + ")");
                    }
                }
                if (model.shouldTrigger(score) && model.canTrigger()) {
                    model.markTrigger();
                    notifyDetection(model.name);
                }
            }
            long now2 = SystemClock.elapsedRealtime();
            if (now2 - lastScoreLogMs >= SCORE_LOG_INTERVAL_MS) {
                lastScoreLogMs = now2;
            }
        }
    }

    private void notifyDetection(String keyword) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onHotwordDetected(keyword));
    }

    private static class WakeWordModel {
        final String name;
        final Interpreter interpreter;
        final int requiredFrames;
        final float threshold;
        private long lastTriggerMs = 0;
        final OpenWakeWordPreprocessor preprocessor;
        // Lissage + adaptation
        private final java.util.ArrayDeque<Float> recentScores = new java.util.ArrayDeque<>();
        private static final int WINDOW_SIZE = 12; // fenêtre plus courte pour capter les pics
        private float adaptiveThreshold; // dynamique dans [MIN_THR, MAX_THR]
        private static final float MIN_THR = 0.20f;
        private static final float MAX_THR = 0.60f;
        private long lastAdaptiveUpdateMs = 0;
        private static final long ADAPT_INTERVAL_MS = 4000;

        WakeWordModel(String name, Interpreter interpreter, int requiredFrames, float threshold, OpenWakeWordPreprocessor preprocessor) {
            this.name = name;
            this.interpreter = interpreter;
            this.requiredFrames = requiredFrames;
            this.threshold = threshold;
            this.preprocessor = preprocessor;
            this.adaptiveThreshold = Math.max(MIN_THR, Math.min(MAX_THR, threshold));
        }

        float run(float[][] featureWindow) {
            if (featureWindow.length < requiredFrames) {
                return 0f;
            }
            float[][][] input = new float[1][requiredFrames][featureWindow[0].length];
            int start = featureWindow.length - requiredFrames;
            for (int i = 0; i < requiredFrames; i++) {
                System.arraycopy(featureWindow[start + i], 0, input[0][i], 0, featureWindow[0].length);
            }
            float[][] output = new float[1][1];
            interpreter.run(input, output);
            return output[0][0];
        }

        void updateScoreHistory(float score) {
            recentScores.addLast(score);
            if (recentScores.size() > WINDOW_SIZE) {
                recentScores.removeFirst();
            }
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastAdaptiveUpdateMs >= ADAPT_INTERVAL_MS) {
                lastAdaptiveUpdateMs = now;
                float avg = getAverageScore();
                // Si très faible activité, on assouplit légèrement
                if (avg < 0.02f) {
                    adaptiveThreshold = Math.max(MIN_THR, adaptiveThreshold - 0.02f);
                }
                // Si activité significative constante, on durcit un peu (éviter FP)
                if (avg > 0.15f) {
                    adaptiveThreshold = Math.min(MAX_THR, adaptiveThreshold + 0.02f);
                }
            }
        }

        float getAverageScore() {
            if (recentScores.isEmpty()) return 0f;
            float sum = 0f;
            for (Float s : recentScores) sum += s;
            return sum / recentScores.size();
        }

        float getEffectiveThreshold() {
            // Pour hey_kitt, utiliser le seuil configuré (plus fiable); sinon adaptatif
            if (name != null && name.toLowerCase(java.util.Locale.ROOT).contains("hey_kitt")) {
                return Math.max(MIN_THR, Math.min(MAX_THR, threshold));
            }
            return Math.max(MIN_THR, Math.min(MAX_THR, adaptiveThreshold));
        }

        boolean shouldTrigger(float score) {
            float eff = getEffectiveThreshold();
            float avg = getAverageScore();
            // Hystérésis: déclenche si pic au-dessus du seuil, OU si moyenne proche du seuil
            if (score >= eff) return true;
            // si la moyenne dépasse 90% du seuil effectif, considérer un déclenchement (robustesse)
            return avg >= (eff * 0.90f);
        }

        boolean canTrigger() {
            return SystemClock.elapsedRealtime() - lastTriggerMs > DEFAULT_DEBOUNCE_MS;
        }

        void markTrigger() {
            lastTriggerMs = SystemClock.elapsedRealtime();
        }

        void close() {
            interpreter.close();
            try {
                if (preprocessor != null) {
                    preprocessor.close();
                }
            } catch (Throwable ignored) {}
        }
    }
}

