package com.raincat.dolby_beta.helper;
import com.raincat.dolby_beta.xposed.XposedCompat;

import android.content.Context;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.FieldsMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.query.matchers.MethodsMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DexKitHelper {
    private static DexKitBridge bridge;

    /** Lazily loads libdexkit.so on first use, then caches the bridge. */
    public static synchronized DexKitBridge getBridge(Context context) {
        if (bridge == null) {
            try {
                System.loadLibrary("dexkit");
            } catch (Throwable t) {
                File so = new File(new File(context.getFilesDir(), "dexkit"), "libdexkit.so");
                if (!so.exists() || so.length() == 0)
                    extractDexKitSo(context, so);
                System.load(so.getAbsolutePath());
            }
            long started = System.currentTimeMillis();
            bridge = DexKitBridge.create(context.getPackageResourcePath());
            XposedCompat.logInfo("DexKit bridge created in "
                    + (System.currentTimeMillis() - started) + "ms");
        }
        return bridge;
    }

    private static void extractDexKitSo(Context context, File target) {
        String apkPath = XposedCompat.getModuleApkPath();
        if (apkPath == null)
            throw new IllegalStateException("module apk path unknown");
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apkPath)) {
            java.util.zip.ZipEntry entry = zip.getEntry("lib/arm64-v8a/libdexkit.so");
            if (entry == null)
                throw new IllegalStateException("libdexkit.so not in module apk");
            File parent = target.getParentFile();
            if (!parent.exists())
                parent.mkdirs();
            java.io.InputStream in = zip.getInputStream(entry);
            java.io.FileOutputStream out = new java.io.FileOutputStream(target);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0)
                out.write(buf, 0, n);
            out.close();
            in.close();
            target.setExecutable(true, false);
            target.setReadable(true, false);
        } catch (Exception e) {
            throw new RuntimeException("extract libdexkit.so failed: " + e, e);
        }
    }

    public static List<String> findBottomTabViewCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .searchPackages("com.netease.cloudmusic")
                    .matcher(ClassMatcher.create()
                            .methods(MethodsMatcher.create()
                                    .add(MethodMatcher.create()
                                            .returnType("java.util.ArrayList", StringMatchType.Equals, false)
                                            .paramTypes())
                                    .add(MethodMatcher.create()
                                            .returnType("[Ljava.lang.String;", StringMatchType.Equals, false)
                                            .paramTypes()))
                            .fields(FieldsMatcher.create()
                                    .add(org.luckypray.dexkit.query.matchers.FieldMatcher.create()
                                            .type("java.lang.String", StringMatchType.Equals, false))
                                    .add(org.luckypray.dexkit.query.matchers.FieldMatcher.create()
                                            .type("java.util.ArrayList", StringMatchType.Equals, false))
                                    .add(org.luckypray.dexkit.query.matchers.FieldMatcher.create()
                                            .type("boolean", StringMatchType.Equals, false)))));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit bottomTab candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit bottomTab: " + t);
            return new ArrayList<>();
        }
    }

    public static List<String> findSidebarItemCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .searchPackages("com.netease.cloudmusic.music.biz.sidebar.account")
                    .matcher(ClassMatcher.create()
                            .methods(MethodsMatcher.create()
                                    .add(MethodMatcher.create()
                                            .returnType("java.util.List", StringMatchType.Equals, false)
                                            .paramTypes())
                                    .add(MethodMatcher.create()
                                            .returnType("java.lang.Throwable", StringMatchType.Equals, false)
                                            .paramTypes()))));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit sidebar candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit sidebar: " + t);
            return new ArrayList<>();
        }
    }

    public static List<String> findAdCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .methods(MethodsMatcher.create()
                                    .add(MethodMatcher.create()
                                            .returnType("VideoAdInfo", StringMatchType.Contains, false))
                                    .add(MethodMatcher.create()
                                            .returnType("com.netease.cloudmusic.meta.Ad", StringMatchType.Equals, false)))));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit ad candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit ad: " + t);
            return new ArrayList<>();
        }
    }

    public static List<String> findAdCardViewHolderCandidates(Context context) {
        try {
            // Never prefix-search "com.netease.cloudmusic.module.ad": DexKit searchPackages is a
            // plain string-prefix match and would also pull in module.addtoplaylist.
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .searchPackages("com.netease.cloudmusic.module.ad.banner",
                            "com.netease.cloudmusic.ui.ad.dslview")
                    .matcher(ClassMatcher.create()
                            .superClass("org.xjy.android.nova.typebind.TypeBindedViewHolder",
                                    StringMatchType.Equals, false)));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit ad card candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit ad card failed");
            XposedCompat.log(t);
            return new ArrayList<>();
        }
    }

    public static List<ClassData> findListenTogetherUnlockCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .searchPackages("com.netease.cloudmusic.module.listentogether")
                    .matcher(ClassMatcher.create()
                            .methods(MethodsMatcher.create()
                                    .add(MethodMatcher.create()
                                            .returnType("boolean", StringMatchType.Equals, false)
                                            .paramTypes()))));
            return new ArrayList<>(list);
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit listenTogether: " + t.getMessage());
            return new ArrayList<>();
        }
    }

    public static List<String> findCronetInterceptorCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .searchPackages("com.netease.cloudmusic.network")
                    .matcher(ClassMatcher.create()
                            .methods(MethodsMatcher.create()
                                    .add(MethodMatcher.create()
                                            .returnType("okhttp3.Response", StringMatchType.Equals, false)
                                            .paramTypes("okhttp3.Interceptor$Chain"))
                                    .add(MethodMatcher.create()
                                            .returnType("okhttp3.Response", StringMatchType.Equals, false)
                                            .paramTypes("org.chromium.net.urlconnection.CronetHttpURLConnection",
                                                    "okhttp3.Request")))));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit Cronet interceptor candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit Cronet interceptor failed: " + t);
            XposedCompat.log(t);
            return new ArrayList<>();
        }
    }

    public static List<String> findPlayServiceCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .addInterface("com.netease.cloudmusic.service.IPlayService")
                            .methods(MethodsMatcher.create()
                                    .add(MethodMatcher.create()
                                            .name("onFakeCompletion")
                                            .returnType("void", StringMatchType.Equals, false)
                                            .paramTypes()))));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit playService candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit playService failed: " + t);
            XposedCompat.log(t);
            return new ArrayList<>();
        }
    }

    public static List<String> findRnContainerCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .methods(MethodsMatcher.create()
                                    .add(MethodMatcher.create()
                                            .returnType("void", StringMatchType.Equals, false)
                                            .paramTypes("android.os.Bundle", "android.view.ViewGroup")))
                            .fields(FieldsMatcher.create()
                                    .add(FieldMatcher.create()
                                            .type("java.lang.String", StringMatchType.Equals, false))
                                    .add(FieldMatcher.create()
                                            .type("java.lang.String", StringMatchType.Equals, false)))));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit RN container candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit RN container: " + t);
            return new ArrayList<>();
        }
    }

    public static List<String> findApiRequestCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .methods(MethodsMatcher.create()
                                    .add(MethodMatcher.create()
                                            .name("p")
                                            .returnType("org.json.JSONObject", StringMatchType.Equals, false)
                                            .paramTypes())
                                    .add(MethodMatcher.create()
                                            .name("s")
                                            .returnType("java.lang.String", StringMatchType.Equals, false)
                                            .paramTypes())
                                    .add(MethodMatcher.create()
                                            .name("p1")
                                            .returnType("java.lang.String", StringMatchType.Equals, false)
                                            .paramTypes()))));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit API request candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit API request failed");
            XposedCompat.log(t);
            return new ArrayList<>();
        }
    }

    public static List<String> findMainNavigationViewModelCandidates(Context context) {
        try {
            ClassDataList list = getBridge(context).findClass(FindClass.create()
                    .matcher(ClassMatcher.create()
                            .usingStrings(java.util.Arrays.asList("BottomTabNewStyle"), StringMatchType.Contains)));
            List<String> out = names(list);
            XposedCompat.logDebug("DexKit main navigation candidates: " + out);
            return out;
        } catch (Throwable t) {
            XposedCompat.log("dolby_beta DexKit main navigation failed");
            XposedCompat.log(t);
            return new ArrayList<>();
        }
    }

    private static List<String> names(List<ClassData> list) {
        List<String> out = new ArrayList<>();
        if (list != null)
            for (ClassData data : list)
                out.add(data.getName());
        return out;
    }
}
