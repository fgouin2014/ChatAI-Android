package com.chatai.hotword;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * Adaptation Android du préprocesseur openWakeWord (melspectrogram + embedding).
 * Convertit le flux PCM 16 kHz en embeddings 96D consommés par les modèles wakeword.
 */
public class OpenWakeWordPreprocessor {

    private static final String TAG = "OWWPreprocessor";
    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_SAMPLES = 1280; // 80 ms
    private static final int MEL_HISTORY = 97 * 10;
    private static final int FEATURE_HISTORY = 160;
    private static final int MEL_WINDOW = 76;
    private static final int MEL_STRIDE = 8;
    private static final int MEL_BINS = 32;
    private static final int EMBEDDING_SIZE = 96;
    private static final float DEFAULT_PRE_EMPHASIS = 0.97f;

    public static final class Profile {
        public final float fMaxHz;
        public final boolean usePreEmphasis;
        public final boolean useCmvn;
        public final boolean useLogDiv10Plus2; // si true: (log10(x)/10 + 2), sinon: log10(x) brut

        public Profile(float fMaxHz, boolean usePreEmphasis, boolean useCmvn, boolean useLogDiv10Plus2) {
            this.fMaxHz = fMaxHz;
            this.usePreEmphasis = usePreEmphasis;
            this.useCmvn = useCmvn;
            this.useLogDiv10Plus2 = useLogDiv10Plus2;
        }

        public static Profile defaultProfile() {
            return new Profile(8000f, true, true, false);
        }
    }

    private final Interpreter embeddingInterpreter;

    private final short[] rawBuffer = new short[SAMPLE_RATE * 10];
    private int rawBufferSize = 0;
    private final short[] remainder = new short[FRAME_SAMPLES];
    private int remainderLength = 0;

    private final List<float[]> melFrames = new ArrayList<>();
    private final List<float[]> featureFrames = new ArrayList<>();

    // ====== CPU Melspectrogram params ======
    private static final int FFT_SIZE = 512;
    private static final int FRAME_LEN = 400;  // 25 ms
    private static final int FRAME_HOP = 160;  // 10 ms
    private final float[] hannWindow = new float[FRAME_LEN];
    private final float[][] melFilterbank = new float[MEL_BINS][FFT_SIZE / 2 + 1];
    private final float[] fftReal = new float[FFT_SIZE];
    private final float[] fftImag = new float[FFT_SIZE];
    private final float[] powerSpec = new float[FFT_SIZE / 2 + 1];

    private final Profile profile;
    private final float preEmphasisCoeff;

    public OpenWakeWordPreprocessor(Context context) throws IOException {
        this(context, Profile.defaultProfile());
    }

    public OpenWakeWordPreprocessor(Context context, Profile profile) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(2);
        options.setUseNNAPI(false);
        try { options.setUseXNNPACK(false); } catch (Throwable ignored) {}

        this.profile = profile != null ? profile : Profile.defaultProfile();
        this.preEmphasisCoeff = this.profile.usePreEmphasis ? DEFAULT_PRE_EMPHASIS : 0f;

        File embeddingFile = HotwordModelLoader.copyAssetToFiles(context, "hotwords/openwakeword/embedding_model.tflite", "hotwords/openwakeword");
        java.nio.MappedByteBuffer embeddingBuffer = HotwordModelLoader.mapFileToMemory(embeddingFile);
        embeddingInterpreter = new Interpreter(embeddingBuffer, options);

        initHann();
        initMelFilterbank();
    }

    public synchronized void pushSamples(short[] samples, int length) {
        int offset = 0;
        while (offset < length) {
            int toCopy = Math.min(FRAME_SAMPLES - remainderLength, length - offset);
            System.arraycopy(samples, offset, remainder, remainderLength, toCopy);
            remainderLength += toCopy;
            offset += toCopy;

            if (remainderLength == FRAME_SAMPLES) {
                processFrame(remainder, FRAME_SAMPLES);
                remainderLength = 0;
            }
        }
    }

    public synchronized float[][] getFeatureWindow(int frameCount) {
        if (featureFrames.size() < frameCount) {
            return null;
        }
        float[][] window = new float[frameCount][EMBEDDING_SIZE];
        int start = featureFrames.size() - frameCount;
        for (int i = 0; i < frameCount; i++) {
            float[] source = featureFrames.get(start + i);
            System.arraycopy(source, 0, window[i], 0, EMBEDDING_SIZE);
        }
        return window;
    }

    public synchronized void reset() {
        melFrames.clear();
        featureFrames.clear();
        rawBufferSize = 0;
        remainderLength = 0;
    }

    public synchronized void close() {
        embeddingInterpreter.close();
    }

    private void processFrame(short[] frame, int length) {
        appendRawSamples(frame, length);

        // Calculer 5 trames mel (25ms fenêtrées, hop 10ms) à partir de 80ms (1280 samples)
        float[][] melOutputs = computeMelFromChunk();
        appendMelFrames(melOutputs);
        generateEmbeddings(1);
    }

    private void appendRawSamples(short[] samples, int length) {
        if (length > rawBuffer.length) {
            System.arraycopy(samples, length - rawBuffer.length, rawBuffer, 0, rawBuffer.length);
            rawBufferSize = rawBuffer.length;
            return;
        }

        if (rawBufferSize + length > rawBuffer.length) {
            int shift = rawBufferSize + length - rawBuffer.length;
            System.arraycopy(rawBuffer, shift, rawBuffer, 0, rawBufferSize - shift);
            rawBufferSize -= shift;
        }

        System.arraycopy(samples, 0, rawBuffer, rawBufferSize, length);
        rawBufferSize += length;
    }

    private float[][] computeMelFromChunk() {
        // Récupère les 1280 derniers samples (ou pad zéro)
        short[] chunk = new short[FRAME_SAMPLES];
        int available = Math.min(rawBufferSize, FRAME_SAMPLES);
        int start = rawBufferSize - available;
        int pad = FRAME_SAMPLES - available;
        // pad leading zeros
        Arrays.fill(chunk, 0, pad, (short) 0);
        for (int i = 0; i < available; i++) {
            chunk[pad + i] = rawBuffer[start + i];
        }

        // 5 frames de 25ms (400) avec hop 160 à partir de 0..640
        float[][] mel = new float[5][MEL_BINS];
        int[] frameStarts = new int[]{0, 160, 320, 480, 640};
        for (int f = 0; f < 5; f++) {
            int s = frameStarts[f];
            // appliquer fenêtre + FFT taille 512
            Arrays.fill(fftReal, 0f);
            Arrays.fill(fftImag, 0f);
            for (int n = 0; n < FRAME_LEN; n++) {
                float x = (s + n) < chunk.length ? (chunk[s + n] / 32768.0f) : 0f;
                float xPrev = (s + n - 1) >= 0 ? (chunk[s + n - 1] / 32768.0f) : 0f;
                float xPre = profile.usePreEmphasis ? (x - preEmphasisCoeff * xPrev) : x;
                fftReal[n] = xPre * hannWindow[n];
            }
            fft(fftReal, fftImag, false);
            // power spectrum (0..256)
            for (int k = 0; k <= FFT_SIZE / 2; k++) {
                float re = fftReal[k];
                float im = fftImag[k];
                powerSpec[k] = re * re + im * im;
            }
            // appliquer filtres mel
            for (int m = 0; m < MEL_BINS; m++) {
                float sum = 0f;
                float[] fb = melFilterbank[m];
                for (int k = 0; k <= FFT_SIZE / 2; k++) {
                    sum += fb[k] * powerSpec[k];
                }
                // Log-mel sécurisé
                float logp = (float) Math.log10(Math.max(sum, 1e-10));
                mel[f][m] = profile.useLogDiv10Plus2 ? (logp / 10.0f + 2.0f) : logp;
            }
        }
        return mel;
    }

    private void appendMelFrames(float[][] frames) {
        for (float[] frame : frames) {
            melFrames.add(frame.clone());
            if (melFrames.size() > MEL_HISTORY) {
                melFrames.remove(0);
            }
        }
    }

    private void generateEmbeddings(int chunkCount) {
        int totalFrames = melFrames.size();
        if (totalFrames < MEL_WINDOW) {
            return;
        }

        for (int i = chunkCount - 1; i >= 0; i--) {
            int endIndex = totalFrames - (i * MEL_STRIDE);
            if (endIndex > totalFrames) {
                endIndex = totalFrames;
            }
            int startIndex = endIndex - MEL_WINDOW;
            if (startIndex < 0 || endIndex > melFrames.size()) {
                continue;
            }
            float[][] window = new float[MEL_WINDOW][MEL_BINS];
            for (int f = 0; f < MEL_WINDOW; f++) {
                System.arraycopy(melFrames.get(startIndex + f), 0, window[f], 0, MEL_BINS);
            }
            float[] embedding = runEmbedding(window);
            featureFrames.add(embedding);
            if (featureFrames.size() > FEATURE_HISTORY) {
                featureFrames.remove(0);
            }
        }
    }

    private float[] runEmbedding(float[][] melWindow) {
        // Copie et normalisation simple (CMVN globale sur la fenêtre)
        float[][][][] input = new float[1][MEL_WINDOW][MEL_BINS][1];
        float sum = 0f, sq = 0f;
        int count = MEL_WINDOW * MEL_BINS;
        for (int i = 0; i < MEL_WINDOW; i++) {
            for (int j = 0; j < MEL_BINS; j++) {
                float v = melWindow[i][j];
                sum += v;
                sq += v * v;
                input[0][i][j][0] = v;
            }
        }
        float mean = sum / Math.max(1, count);
        float var = Math.max(1e-6f, (sq / Math.max(1, count)) - mean * mean);
        float std = (float) Math.sqrt(var);
        for (int i = 0; i < MEL_WINDOW; i++) {
            for (int j = 0; j < MEL_BINS; j++) {
                float v = (input[0][i][j][0] - mean) / std;
                // clamp
                if (v > 4f) v = 4f;
                if (v < -4f) v = -4f;
                input[0][i][j][0] = v;
            }
        }
        float[][][][] output = new float[1][1][1][EMBEDDING_SIZE];
        embeddingInterpreter.run(input, output);
        float[] vector = new float[EMBEDDING_SIZE];
        System.arraycopy(output[0][0][0], 0, vector, 0, EMBEDDING_SIZE);
        return vector;
    }

    // ====== Utilitaires FFT & Mel ======
    private void initHann() {
        for (int n = 0; n < FRAME_LEN; n++) {
            hannWindow[n] = (float) (0.5 * (1 - Math.cos(2 * Math.PI * n / (FRAME_LEN - 1))));
        }
    }

    private void initMelFilterbank() {
        // Générer 32 filtres mel triangulaires ~ [fmin=20 Hz, fmax=profile.fMaxHz] pour SR=16k
        int nfft = FFT_SIZE;
        int nfreqs = nfft / 2 + 1;
        float fMin = 20f;
        float fMax = Math.max(1000f, Math.min(8000f, profile.fMaxHz));
        // mel scale helpers
        java.util.function.DoubleUnaryOperator hz2mel = hz -> 2595.0 * Math.log10(1.0 + hz / 700.0);
        java.util.function.DoubleUnaryOperator mel2hz = mel -> 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0);

        double melMin = hz2mel.applyAsDouble(fMin);
        double melMax = hz2mel.applyAsDouble(fMax);
        double[] melPoints = new double[MEL_BINS + 2];
        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = melMin + (melMax - melMin) * i / (MEL_BINS + 1);
        }
        double[] hzPoints = new double[melPoints.length];
        for (int i = 0; i < hzPoints.length; i++) {
            hzPoints[i] = mel2hz.applyAsDouble(melPoints[i]);
        }
        int[] bin = new int[hzPoints.length];
        for (int i = 0; i < hzPoints.length; i++) {
            bin[i] = (int) Math.floor((FFT_SIZE + 1) * hzPoints[i] / SAMPLE_RATE);
            if (bin[i] < 0) bin[i] = 0;
            if (bin[i] > nfreqs - 1) bin[i] = nfreqs - 1;
        }
        for (int m = 1; m <= MEL_BINS; m++) {
            int f_m_minus = bin[m - 1];
            int f_m = bin[m];
            int f_m_plus = bin[m + 1];
            Arrays.fill(melFilterbank[m - 1], 0f);
            for (int k = f_m_minus; k < f_m; k++) {
                melFilterbank[m - 1][k] = (float) ((k - f_m_minus) / (double) (f_m - f_m_minus + 1e-9));
            }
            for (int k = f_m; k < f_m_plus; k++) {
                melFilterbank[m - 1][k] = (float) ((f_m_plus - k) / (double) (f_m_plus - f_m + 1e-9));
            }
        }
    }

    // In-place radix-2 Cooley–Tukey FFT (real+imag arrays). dir=false => forward.
    private void fft(float[] real, float[] imag, boolean inverse) {
        int n = real.length;
        // bit reversal
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                float tr = real[i]; real[i] = real[j]; real[j] = tr;
                float ti = imag[i]; imag[i] = imag[j]; imag[j] = ti;
            }
            int m = n >> 1;
            while (j >= m && m >= 1) { j -= m; m >>= 1; }
            j += m;
        }
        // stages
        for (int len = 2; len <= n; len <<= 1) {
            double ang = 2 * Math.PI / len * (inverse ? -1 : 1);
            float wlen_r = (float) Math.cos(ang);
            float wlen_i = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float wr = 1f, wi = 0f;
                for (int k = 0; k < len / 2; k++) {
                    int u = i + k;
                    int v = i + k + len / 2;
                    float vr = real[v] * wr - imag[v] * wi;
                    float vi = real[v] * wi + imag[v] * wr;
                    real[v] = real[u] - vr;
                    imag[v] = imag[u] - vi;
                    real[u] = real[u] + vr;
                    imag[u] = imag[u] + vi;
                    float nxt_wr = wr * wlen_r - wi * wlen_i;
                    float nxt_wi = wr * wlen_i + wi * wlen_r;
                    wr = nxt_wr; wi = nxt_wi;
                }
            }
        }
        if (inverse) {
            for (int i = 0; i < n; i++) {
                real[i] /= n;
                imag[i] /= n;
            }
        }
    }
}

