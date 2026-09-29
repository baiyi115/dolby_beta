package com.raincat.dolby_beta.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.text.TextUtils;

import com.raincat.dolby_beta.BuildConfig;
import com.raincat.dolby_beta.net.HTTPSTrustManager;
import com.raincat.dolby_beta.utils.Tools;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.stericson.RootShell.execution.Command;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.FileLock;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

public class ScriptHelper {

    public static String modulePath;

    private static String scriptPath;

    private static String nodeLibPath;

    private static final String SCRIPT_ASSET_VERSION = "2026092705";

    private static final String[] STOP_PROXY = new String[]{
            "killall -9 libnode.so >/dev/null 2>&1 || true"
    };

    private static final AtomicBoolean proxyReadyMonitorStarted = new AtomicBoolean(false);

    @SuppressLint("StaticFieldLeak")
    private static Context neteaseContext;

    private static String getScriptPath(Context context) {
        if (TextUtils.isEmpty(scriptPath))
            scriptPath = context.getFilesDir().getAbsolutePath() + "/script";
        return scriptPath;
    }

    public static void initScript(Context context, boolean cover) {
        File unblockFile = new File(getScriptPath(context));
        neteaseContext = context;
        boolean scriptChanged = !SCRIPT_ASSET_VERSION.equals(ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_ASSET_VERSION))
                || !(BuildConfig.VERSION_CODE + "").equals(ExtraHelper.getExtraDate(ExtraHelper.APP_VERSION));
        if (cover || !unblockFile.exists() || scriptChanged)
            releaseScript(context);

        if (TextUtils.isEmpty(nodeLibPath)) {
            nodeLibPath = TextUtils.isEmpty(modulePath) ? "" : modulePath.substring(0, modulePath.lastIndexOf('/'));
            nodeLibPath = "export PATH=$PATH:" + nodeLibPath + "/lib/arm64:" + modulePath + "!/lib/arm64-v8a:" + context.getApplicationInfo().nativeLibraryDir;
        }
    }

    private static final Object EXTRACT_LOCK = new Object();

    /**
     * The main process and {@code :play} share one files dir and both call this during startup, so
     * extraction has to be serialized across processes rather than only across threads: two
     * concurrent runs wrote the same .unm_script.tmp and unzipped into the same directory. A lock
     * file next to the script works because both processes run under the same uid.
     */
    private static void releaseScript(Context context) {
        synchronized (EXTRACT_LOCK) {
            RandomAccessFile lockFile = null;
            FileLock lock = null;
            try {
                File dir = new File(getScriptPath(context));
                if (!dir.exists() && !dir.mkdirs())
                    XposedCompat.logError("UNM script dir unavailable: " + dir);
                lockFile = new RandomAccessFile(new File(dir, ".unm.lock"), "rw");
                lock = lockFile.getChannel().tryLock();
                if (lock == null) {
                    // Another process is extracting right now; it publishes the same asset version,
                    // and a failure there is retried on the next start.
                    XposedCompat.logInfo("UNM script extraction in progress in another process, skipping");
                } else if (extractScript(context) && verifyScript()) {
                    Command auth = new Command(0, "cd " + getScriptPath(context), "chmod 0777 *");
                    Tools.shell(auth);
                    ExtraHelper.setExtraDate(ExtraHelper.APP_VERSION, BuildConfig.VERSION_CODE);
                    ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_ASSET_VERSION, SCRIPT_ASSET_VERSION);
                    XposedCompat.logInfo("UNM script released: " + getScriptPath(context));
                } else {
                    XposedCompat.logError("UNM script release failed, keep current files and retry on next start");
                }
            } catch (Throwable t) {
                XposedCompat.log("UNM script release threw");
                XposedCompat.log(t);
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (Throwable ignored) {
                    }
                }
                if (lockFile != null) {
                    try {
                        lockFile.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    private static boolean extractScript(Context context) {
        File temp = new File(getScriptPath(context), ".unm_script.tmp");
        long copied = copyModuleAsset(context, temp);
        if (copied <= 0) {
            XposedCompat.logError("UNM asset copy via AssetManager failed (" + copied + " bytes), fallback to ZipFile");
            if (!FileHelper.unzipFile(modulePath, getScriptPath(context), "assets", "UnblockNeteaseMusic.zip")) {
                XposedCompat.logError("UNM asset copy via ZipFile failed too");
                return false;
            }
            File legacy = new File(getScriptPath(context), "UnblockNeteaseMusic.zip");
            if (!legacy.renameTo(temp)) {
                XposedCompat.logError("UNM asset rename failed");
                return false;
            }
        } else {
            XposedCompat.logInfo("UNM asset copied: " + copied + " bytes");
        }
        boolean extracted;
        try {
            extracted = FileHelper.unzipFiles(temp.getAbsolutePath(), getScriptPath(context));
        } finally {

            if (!temp.delete())
                temp.deleteOnExit();
        }
        if (!extracted) {
            XposedCompat.logError("UNM script zip extraction failed");
            return false;
        }
        return true;
    }

    private static long copyModuleAsset(Context context, File target) {
        try {
            AssetManager assets = AssetManager.class.newInstance();
            AssetManager.class.getMethod("addAssetPath", String.class).invoke(assets, modulePath);
            try (InputStream is = assets.open("UnblockNeteaseMusic.zip");
                 FileOutputStream fos = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int len;
                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    total += len;
                }
                fos.flush();
                return total;
            }
        } catch (Throwable t) {
            XposedCompat.log(t);
            return -1;
        }
    }

    private static boolean verifyScript() {
        File app = new File(getScriptPath(neteaseContext), "app.js");
        boolean ok = app.exists() && app.length() > 100_000;
        if (!ok)
            XposedCompat.logError("UNM script verify failed: app.js size="
                    + (app.exists() ? app.length() : -1));
        return ok;
    }

    public static void startHttpProxyMode(final Context context) {
        stopScript();
        final String host = SettingHelper.getInstance().getHttpProxy();
        final int port = SettingHelper.getInstance().getProxyPort();
        XposedCompat.logInfo("UNM external proxy checking: host="
                + SettingHelper.getInstance().getHttpProxy()
                + " port=" + port
                + " serverMode=" + SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key));
        new Thread(() -> {
            if (isProxyReachable(host, port)) {
                ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "1");
                XposedCompat.logInfo("UNM external proxy ready: " + host + ":" + port);
                clearHostSongUrlInfoCache(neteaseContext);
                Tools.showToastOnLooper(context, "服务器代理运行成功");
            } else {
                ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0");
                XposedCompat.logError("UNM external proxy unavailable: " + host + ":" + port);
                Tools.showToastOnLooper(context, "服务器代理不可用，请检查地址和端口");
            }
        }, "UNM-external-proxy-check").start();
    }

    private static final String[] VALID_SOURCES = new String[]{"qq", "kugou", "kuwo", "bodian", "migu", "joox",
            "youtube", "youtubedl", "ytdlp", "bilibili", "bilivideo", "pyncmd"};

    private static String sanitizeSources(String original) {
        StringBuilder sources = new StringBuilder();
        if (original != null)
            for (String source : original.split("\\s+"))
                for (String valid : VALID_SOURCES)
                    if (valid.equals(source)) {
                        if (sources.length() > 0)
                            sources.append(' ');
                        sources.append(source);
                        break;
                    }
        return sources.length() > 0 ? sources.toString() : "pyncmd kuwo bodian";
    }

    /**
     * Cookies are interpolated straight into a shell command, so anything that could terminate the
     * surrounding double quotes (or start a substitution) has to go. Real cookie values only use
     * the characters kept here, so this is lossless for them and neutralises a pasted payload.
     */
    private static String sanitizeShellValue(String value) {
        if (value == null)
            return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '=' || c == ';' || c == '_' || c == '-' || c == '.' || c == ':'
                    || c == '+' || c == '/' || c == '*' || c == '%' || c == ',' || c == '~' || c == ' ')
                out.append(c);
        }
        return out.toString();
    }

    public static void startScript() {
        final int port = SettingHelper.getInstance().getProxyPort();
        XposedCompat.logInfo("UNM local script starting: scriptPath=" + scriptPath
                + " port=" + port
                + " sources=" + sanitizeSources(SettingHelper.getInstance().getProxyOriginal()));

        String script = String.format(
                "export ENABLE_FLAC=%s&&export MIN_BR=%s&&export QQ_COOKIE=\"%s\"&&export MIGU_COOKIE=\"%s\""
                        + "&&export SIGN_CERT=\"%s/server.crt\"&&export SIGN_KEY=\"%s/server.key\""
                        + "&&libnode.so app.js -a 127.0.0.1 -e - -o %s -p %s",
                SettingHelper.getInstance().getSetting(SettingHelper.proxy_flac_key), SettingHelper.getInstance().getSetting(SettingHelper.proxy_priority_key) ? "256000" : "96000",
                sanitizeShellValue(SettingHelper.getInstance().getQqCookie()),
                sanitizeShellValue(SettingHelper.getInstance().getMiguCookie()),
                scriptPath, scriptPath,
                sanitizeSources(SettingHelper.getInstance().getProxyOriginal()),
                SettingHelper.getInstance().getProxyPort() + ":" + (SettingHelper.getInstance().getProxyPort() + 1));

        script = script + " 2>&1";

        String[] START_PROXY = new String[]{
                "killall -9 libnode.so >/dev/null 2>&1 || true",
                "sleep 1",
                "cd " + scriptPath,
                nodeLibPath + "&&" + script
        };
            Command start = new Command(0, START_PROXY) {
            @Override
            public void commandOutput(int id, String line) {
                XposedCompat.logDebug("UNM: " + line);
                if ((!line.contains("mERROR") && line.contains("Error:")) || line.contains("Port ") || line.contains("Please ")) {

                    if (line.contains("ENOTFOUND") || line.contains("AggregateError")
                            || line.contains("provider/match") || line.contains("TypeError")
                            || line.contains("ECONNREFUSED") || line.contains("ETIMEDOUT")) {
                        XposedCompat.logError("UNM source match failure: " + line.trim());
                        return;
                    }
                    Intent intent = new Intent(ProcessBroadcast.SEND_NOTIFICATION);
                    intent.putExtra("message", line);
                    intent.putExtra("title", "脚本产生如下错误信息，若脚本因此无法运行请提issue");
                    if (neteaseContext != null)
                        neteaseContext.sendBroadcast(intent);
                } else if (line.contains("HTTP Server running")) {
                    XposedCompat.logInfo("UNM script running");
                    if (neteaseContext != null && ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS).equals("0"))
                        Tools.showToastOnLooper(neteaseContext, "UnblockNeteaseMusic运行成功");
                    ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "1");
                    clearHostSongUrlInfoCache(neteaseContext);
                } else if (line.equals("RESTART")) {
                    XposedCompat.logInfo("UNM script restart detected");
                    ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0");
                }
            }
        };

        XposedCompat.logInfo("UNM local script command submitted");
        Tools.shell(start);
        watchProxyReady(neteaseContext, port);
    }

    private static void watchProxyReady(final Context context, final int port) {
        if (!proxyReadyMonitorStarted.compareAndSet(false, true))
            return;
        new Thread(() -> {
            for (int i = 0; i < 30; i++) {
                if (isProxyReachable("127.0.0.1", port)) {
                    ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "1");
                    XposedCompat.logInfo("UNM proxy ready by port probe: 127.0.0.1:" + port);
                    clearHostSongUrlInfoCache(context);
                    break;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if ("0".equals(ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS)))
                XposedCompat.logError("UNM proxy not ready after 15s: 127.0.0.1:" + port);
            proxyReadyMonitorStarted.set(false);
        }, "UNM-proxy-ready").start();
    }

    public static void stopScript() {
        Tools.shell(new Command(0, STOP_PROXY));
    }

    public static void clearHostSongUrlInfoCache(Context context) {
        if (context == null) return;
        try {
            Class<?> bridgeClass = Class.forName("uj0.u0", false, context.getClassLoader());
            Object bridge = getBridgeInstance(bridgeClass);
            if (bridge == null)
                throw new IllegalStateException("uj0.u0 bridge instance not found");
            bridgeClass.getDeclaredMethod("c").invoke(bridge);
            XposedCompat.logInfo("UNM ready, host song url cache cleared");
        } catch (Throwable t) {
            XposedCompat.log("UNM clear song url cache failed");
            XposedCompat.log(t);
        }
    }

    private static Object getBridgeInstance(Class<?> bridgeClass) throws Exception {
        for (Field field : bridgeClass.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    || !bridgeClass.isAssignableFrom(field.getType()))
                continue;
            field.setAccessible(true);
            Object value = field.get(null);
            if (value != null)
                return value;
        }
        return null;
    }

    private static boolean isProxyReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
            return socket.isConnected();
        } catch (Throwable t) {
            // Expected while the node process is still binding the port. watchProxyReady()
            // reports the final verdict, so a stack trace per probe is pure noise.
            XposedCompat.logDebug("UNM proxy not reachable yet: " + host + ":" + port);
            return false;
        }
    }

    public static SSLSocketFactory getSSLSocketFactory(Context context) {
        SSLContext sslContext = null;
        try {

            TrustManager[] trustManagers = new TrustManager[]{new HTTPSTrustManager()};
            try {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagers, new SecureRandom());
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                XposedCompat.log("UNM SSL init failed");
                XposedCompat.log(e);
            }
        } catch (Exception e) {
            XposedCompat.log("UNM SSL factory init failed");
            XposedCompat.log(e);
        }

        if (sslContext != null)
            return sslContext.getSocketFactory();
        else
            return null;
    }
}
