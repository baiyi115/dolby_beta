package com.raincat.dolby_beta.helper;

import android.os.Environment;

import java.io.File;
import java.io.IOException;

/**
 * Ad-cache cleanup and tinker neutralisation, shared by the main installer and the lite/Honor
 * installer. Only the package name and the ad cache locations differ between them.
 */
public final class AdCleanupHelper {

    private AdCleanupHelper() {
    }

    /** Ad cache directory inside the package's external data directory. */
    public static String externalAdCache(String packageName) {
        return Environment.getExternalStorageDirectory() + "/Android/data/" + packageName + "/cache/Ad";
    }

    public static void deleteAdAndTinker(String packageName, String... adCachePaths) throws IOException {
        for (String path : adCachePaths)
            FileHelper.deleteDirectory(path);

        String tinkerPath = "data/data/" + packageName + "/tinker";
        File tinkerFile = new File(tinkerPath);
        if (tinkerFile.exists() && tinkerFile.isDirectory())
            FileHelper.deleteDirectory(tinkerPath);
        if (!tinkerFile.exists())
            tinkerFile.createNewFile();

        Process process = null;
        try {
            process = Runtime.getRuntime().exec("chmod 000 " + tinkerFile.getAbsolutePath());
        } finally {
            // The process itself is not waited for, but its three pipes must be released or the
            // file descriptors stay open for the lifetime of the host process.
            if (process != null) {
                closeQuietly(process.getInputStream());
                closeQuietly(process.getErrorStream());
                closeQuietly(process.getOutputStream());
            }
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }
}
