package com.chatai.hotword;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

final class HotwordModelLoader {

    private static final String TAG = "HotwordModelLoader";

    private HotwordModelLoader() {
    }

    static ByteBuffer loadModel(Context context, String assetPath) throws IOException {
        AssetManager manager = context.getAssets();
        try (InputStream inputStream = manager.open(assetPath);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            byte[] bytes = outputStream.toByteArray();
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
            byteBuffer.put(bytes);
            byteBuffer.rewind();
            return byteBuffer;
        }
    }

    /**
     * Copie un asset binaire vers le répertoire interne de l'app et renvoie le fichier résultant.
     * Cette variante est préférable pour TensorFlow Lite car le chargement par fichier permet le mmap.
     */
    static File copyAssetToFiles(Context context, String assetPath, String outDirName) throws IOException {
        AssetManager manager = context.getAssets();
        File outDir = new File(context.getFilesDir(), outDirName);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Failed to create dir: " + outDir.getAbsolutePath());
        }
        String fileName = new File(assetPath).getName();
        File outFile = new File(outDir, fileName);

        if (outFile.exists() && outFile.length() > 0) {
            return outFile;
        }

        try (InputStream inputStream = manager.open(assetPath);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
            fos.flush();
        } catch (IOException e) {
            // Nettoyage si copie partielle
            try {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            } catch (Exception ignored) {}
            throw e;
        }

        if (outFile.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
            throw new IOException("Copied zero-length file for asset: " + assetPath);
        }
        Log.i(TAG, "Asset copied: " + assetPath + " -> " + outFile.getAbsolutePath());
        return outFile;
    }

    /**
     * Mappe un fichier en mémoire (read-only) pour TensorFlow Lite.
     */
    static MappedByteBuffer mapFileToMemory(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             FileChannel channel = fis.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }
}

