package com.raincat.dolby_beta.helper;
import com.raincat.dolby_beta.xposed.XposedCompat;
import static com.raincat.dolby_beta.xposed.XposedCompat.*;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.annimon.stream.Stream;

import com.raincat.dolby_beta.utils.Tools;
import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedClassDef;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ClassHelper {

    private static ClassLoader classLoader = null;

    private static List<String> classCacheList = null;

    private static String classCachePath = null;

    private static int versionCode = 0;

    public static synchronized void getCacheClassList(final Context context, final int version, final OnCacheClassListener listener) {
        if (classLoader == null) {
            classLoader = context.getClassLoader();
            versionCode = version;
            File cacheFile = Objects.requireNonNull(context.getExternalFilesDir(null));
            if (cacheFile.exists() || cacheFile.mkdirs())
                classCachePath = cacheFile.getPath();
        }
        if (classCacheList == null) {
            classCacheList = FileHelper.readFileFromSD(classCachePath + File.separator + "class-" + version);
            if (classCacheList.size() == 0) {
                new Thread(() -> getCacheClassByZip(context, version, listener), "dolby-dexscan").start();
            } else
                runDeferred(listener);
        } else
            runDeferred(listener);
        XposedCompat.logInfo("ClassHelper cache ready process=" + Tools.getCurrentProcessName(context)
                + " version=" + version + " count=" + classCacheList.size());
    }

    /**
     * Deferred hooks install here. The cache-hit path used to run them on the caller's thread — the
     * app main thread during attachBaseContext — and that phase includes DexKit bridge creation
     * (measured 176-501ms) plus class scans, which must not block the first frame. The thread
     * starts at the same instant the synchronous call would have, so hooks are not installed later
     * in wall-clock terms; the main thread simply stops waiting for them.
     */
    private static void runDeferred(OnCacheClassListener listener) {
        new Thread(listener::onGet, "dolby-deferred-hooks").start();
    }

    private static synchronized void getCacheClassByZip(Context context, int version, OnCacheClassListener listener) {
        try {

            File appInstallFile = new File(context.getPackageResourcePath());
            Enumeration<? extends ZipEntry> zip = new ZipFile(appInstallFile).entries();
            while (zip.hasMoreElements()) {
                ZipEntry dexInZip = zip.nextElement();
                if (dexInZip.getName().startsWith("classes") && dexInZip.getName().endsWith(".dex")) {
                    MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = DexFileFactory.loadDexEntry(appInstallFile, dexInZip.getName(), true, null);
                    DexBackedDexFile dexFile = dexEntry.getDexFile();
                    for (DexBackedClassDef classDef : dexFile.getClasses()) {
                        String classType = classDef.getType();
                        if (classType.contains("com/netease/cloudmusic") || classType.contains("okhttp3")) {
                            classType = classType.substring(1, classType.length() - 1).replace("/", ".");
                            classCacheList.add(classType);
                        }
                    }
                }
            }
        } catch (Exception e) {
            XposedCompat.log("ClassHelper dex cache scan failed: " + e);
            XposedCompat.log(e);
        } finally {
            FileHelper.writeFileFromSD(classCachePath + File.separator + "class-" + version, classCacheList);
            listener.onGet();
            XposedCompat.logInfo("ClassHelper dex scan complete process=" + Tools.getCurrentProcessName(context)
                    + " version=" + version + " count=" + classCacheList.size());
        }
    }

    public interface OnCacheClassListener {
        void onGet();
    }

    public static List<String> getFilteredClasses(Pattern pattern, Comparator<String> comparator) {
        // classCacheList is only filled by the (async) dex scan, so callers can get here before it
        // exists — that used to be a NullPointerException instead of an empty result.
        List<String> cached = classCacheList;
        if (cached == null || cached.isEmpty())
            return new ArrayList<>();
        List<String> list = Stream.of(cached)
                .filter(s -> s != null && pattern.matcher(s).find())
                .toList();
        Collections.sort(list, comparator);
        return list;
    }

    private static Class<?> getClassByXposed(String className) {
        Class<?> clazz = findClassIfExists(className, classLoader);
        if (clazz == null)
            clazz = findClassIfExists("com.netease.cloudmusic.NeteaseMusicApplication", classLoader);
        return clazz;
    }

    public static class Cookie {
        private static Class<?> clazz, abstractClazz;

        public static String getCookie(Context context) {

            if (versionCode >= 9000000) {
                try {
                    Class<?> storeClass = findClassIfExists("com.netease.cloudmusic.network.cookie.store.CloudMusicCookieStore", classLoader);
                    if (storeClass != null) {
                        Object store = XposedCompat.callStaticMethod(storeClass, "getInstance");
                        Object cookieString = XposedCompat.callMethod(store, "getUserLoginCookie");
                        if (cookieString != null && cookieString.toString().length() > 0)
                            return "MUSIC_U=" + cookieString;
                    }
                } catch (Throwable t) {
                    XposedCompat.log("ClassHelper.Cookie getUserLoginCookie failed");
                    XposedCompat.log(t);
                }
            }
            if (clazz == null) {
                Pattern pattern;
                if (versionCode < 154)
                    pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]\\.[a-z]$");
                else if (versionCode < 8008050)
                    pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]\\.[a-z]\\.[a-z]$");
                else
                    pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.cookie\\.store\\.[a-zA-Z0-9]{1,25}$");

                List<String> list = getFilteredClasses(pattern, null);

                try {
                    abstractClazz = Stream.of(list)
                            .map(ClassHelper::getClassByXposed)
                            .filter(c -> Modifier.isPublic(c.getModifiers()))
                            .filter(c -> c.getSuperclass() == Object.class)
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == ConcurrentHashMap.class))
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == SharedPreferences.class))
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == long.class))
                            .findFirst()
                            .get();

                  if (versionCode >= 154) {
                        clazz = Stream.of(list)
                                .map(ClassHelper::getClassByXposed)
                                .filter(c -> Modifier.isPublic(c.getModifiers()))
                                .filter(m -> !Modifier.isInterface(m.getModifiers()))
                                .filter(c -> c.getSuperclass() == abstractClazz)
                                .findFirst()
                                .get();
                    } else {
                        clazz = abstractClazz;
                    }
                } catch (NoSuchElementException e) {
                    MessageHelper.sendNotification(context, MessageHelper.cookieClassNotFoundCode);
                }
            }

            Object cookieString = null;
          if (versionCode >= 154) {

                Method cookieMethod = XposedCompat.findMethodsByExactParameters(clazz, clazz)[0];
                Object cookie = XposedCompat.callStaticMethod(clazz, cookieMethod.getName());
              for (Method method : XposedCompat.findMethodsByExactParameters(abstractClazz, String.class)) {
                    if (method.getTypeParameters().length == 0 && method.getModifiers() == Modifier.PUBLIC) {
                        cookieString = XposedCompat.callMethod(cookie, method.getName());
                    }
                }
            } else {
                Method cookieMethod = XposedCompat.findMethodsByExactParameters(clazz, String.class)[0];
                cookieString = XposedCompat.callStaticMethod(clazz, cookieMethod.getName());
            }

            return "MUSIC_U=" + cookieString;
        }
    }

    public static class DownloadTransfer {
        private static Method checkMd5Method;
        private static Method checkDownloadStatusMethod;

        public static Method getCheckMd5Method(Context context) {
            if (checkMd5Method == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.transfer\\.download\\.[a-z0-9]{1,2}$");
                List<String> list = ClassHelper.getFilteredClasses(pattern, Collections.reverseOrder());

                try {
                    checkMd5Method = Stream.of(list)
                            .map(c -> getClassByXposed(c).getDeclaredMethods())
                            .flatMap(Stream::of)
                            .filter(m -> m.getParameterTypes().length == 4)
                            .filter(m -> m.getParameterTypes()[0] == File.class)
                            .filter(m -> m.getParameterTypes()[1] == File.class)
                            .findFirst()
                            .get();
                } catch (NoSuchElementException e) {
                    MessageHelper.sendNotification(context, MessageHelper.transferClassNotFoundCode);
                }
            }
            return checkMd5Method;
        }

        public static Method getCheckDownloadStatusMethod(Context context) {
            if (checkDownloadStatusMethod == null) {
                Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.transfer\\.download\\.[a-z0-9]{1,2}$");
                List<String> list = ClassHelper.getFilteredClasses(pattern, Collections.reverseOrder());

                try {
                    checkDownloadStatusMethod = Stream.of(list)
                            .map(c -> getClassByXposed(c).getDeclaredMethods())
                            .flatMap(Stream::of)
                            .filter(m -> m.getReturnType() == long.class)
                            .filter(m -> m.getParameterTypes().length == 5)
                            .filter(m -> m.getParameterTypes()[1] == int.class)
                            .filter(m -> m.getParameterTypes()[3] == File.class)
                            .filter(m -> m.getParameterTypes()[4] == long.class)
                            .findFirst()
                            .get();
                } catch (NoSuchElementException e) {
                    MessageHelper.sendNotification(context, MessageHelper.transferClassNotFoundCode);
                }
            }
            return checkDownloadStatusMethod;
        }
    }

    public static class MainActivitySuperClass {
        private static Class<?> clazz;
        private static List<Method> methods;
        private static Method method;

        static void getClazz(Context context) {
            if (clazz == null) {
                Class<?> mainActivityClass = findClass("com.netease.cloudmusic.activity.MainActivity", context.getClassLoader());
                clazz = mainActivityClass.getSuperclass();
            }
        }

        public static List<Method> getTabItemStringMethods(Context context) {
            if (clazz == null)
                getClazz(context);
            if (methods == null && clazz != null) {
                List<Method> methodList = Arrays.asList(clazz.getDeclaredMethods());
                methods = Stream.of(methodList)
                        .filter(m -> m.getParameterTypes().length >= 1)
                        .filter(m -> m.getReturnType() == void.class)
                        .filter(m -> m.getParameterTypes()[0] == String[].class)
                        .filter(m -> Modifier.isPublic(m.getModifiers()))
                        .toList();
            }
            return methods;
        }

        public static String getSuperClassName() {
            return clazz == null ? "null" : clazz.getName();
        }

        public static Method getViewPagerInitMethod(Context context) {
            if (method == null) {
                try {
                    List<Method> methodList = Arrays.asList(findClass("com.netease.cloudmusic.activity.MainActivity", context.getClassLoader()).getDeclaredMethods());
                    method = Stream.of(methodList)
                            .filter(m -> m.getParameterTypes().length == 1)
                            .filter(m -> m.getReturnType() == void.class)
                            .filter(m -> m.getParameterTypes()[0] == Intent.class)
                            .filter(m -> Modifier.isPrivate(m.getModifiers()))
                            .findFirst()
                            .get();
                } catch (Exception e) {
                    MessageHelper.sendNotification(context, MessageHelper.tabClassNotFoundCode);
                }
            }
            return method;
        }
    }

    public static class BottomTabView {
        private static Class<?> clazz;
        private static Method initMethod, refreshMethod;

        public static Class<?> getClazz(Context context) {
            if (clazz == null) {
                try {
                    List<String> list = DexKitHelper.findBottomTabViewCandidates(context);
                    if (list.isEmpty()) {
                    Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.[a-z0-9]{1,2}\\.[a-z]$");
                    Pattern pattern2 = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z0-9]{1,2}\\.[a-z]\\.[a-z]$");
                    Pattern pattern3 = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.main\\.[a-z]$");
                    list = ClassHelper.getFilteredClasses(pattern, Collections.reverseOrder());
                    list.addAll(ClassHelper.getFilteredClasses(pattern2, Collections.reverseOrder()));
                    list.addAll(ClassHelper.getFilteredClasses(pattern3, Collections.reverseOrder()));
                    }
                    clazz = Stream.of(list)
                            .map(ClassHelper::getClassByXposed)
                            .filter(c -> Modifier.isPublic(c.getModifiers()))
                            .filter(m -> Modifier.isFinal(m.getModifiers()))
                            .filter(m -> !Modifier.isInterface(m.getModifiers()))
                            .filter(m -> !Modifier.isStatic(m.getModifiers()))
                            .filter(m -> !Modifier.isAbstract(m.getModifiers()))
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == String.class))
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == ArrayList.class))
                            .filter(c -> Stream.of(c.getDeclaredFields()).anyMatch(m -> m.getType() == boolean.class))
                            .filter(c -> Stream.of(c.getDeclaredMethods()).anyMatch(m -> m.getReturnType() == ArrayList.class && Modifier.isFinal(m.getModifiers()) && m.getParameterTypes().length == 0))
                            .filter(c -> Stream.of(c.getDeclaredMethods()).anyMatch(m -> m.getReturnType() == String[].class && Modifier.isFinal(m.getModifiers()) && m.getParameterTypes().length == 0))
                            .findFirst()
                            .get();
                } catch (NoSuchElementException e) {
                    MessageHelper.sendNotification(context, MessageHelper.tabClassNotFoundCode);
                }
            }
            return clazz;
        }

        public static Method getTabInitMethod(Context context) {
            if (initMethod == null) {
                // getClazz() may legitimately return null (nothing matched), and callers use the
                // result without a null check, so never dereference it here.
                if (clazz == null) {
                    MessageHelper.sendNotification(context, MessageHelper.tabClassNotFoundCode);
                    return null;
                }
                Method[] methods = findMethodsByExactParameters(clazz, ArrayList.class);
                if (methods.length != 0)
                    initMethod = methods[0];
                else
                    MessageHelper.sendNotification(context, MessageHelper.tabClassNotFoundCode);
            }
            return initMethod;
        }

        public static Method getTabRefreshMethod(Context context) {
            if (refreshMethod == null) {
                if (clazz == null) {
                    MessageHelper.sendNotification(context, MessageHelper.tabClassNotFoundCode);
                    return null;
                }
                Method[] methods = findMethodsByExactParameters(clazz, void.class, List.class);
                if (methods.length != 0)
                    refreshMethod = methods[0];
                else
                    MessageHelper.sendNotification(context, MessageHelper.tabClassNotFoundCode);
            }
            return refreshMethod;
        }
    }

    public static class Ad {
        private static Class<?> adClazz;
        private static Class<?> clazz;

        public static Class<?> getClazz(Context context) {
            if (clazz == null) {
                adClazz = getClassByXposed("com.netease.cloudmusic.meta.Ad");
                try {
                    List<String> list = DexKitHelper.findAdCandidates(context);
                    if (list.isEmpty()) {
                    Pattern pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.ad\\.[a-z]$");
                    list = ClassHelper.getFilteredClasses(pattern, Collections.reverseOrder());
                    }
                    clazz = Stream.of(list)
                            .map(ClassHelper::getClassByXposed)
                            .filter(c -> Modifier.isPublic(c.getModifiers()))
                            .filter(m -> !Modifier.isInterface(m.getModifiers()))
                            .filter(m -> !Modifier.isStatic(m.getModifiers()))
                            .filter(m -> !Modifier.isAbstract(m.getModifiers()))
                            .filter(c -> Stream.of(c.getDeclaredMethods()).anyMatch(m -> m.getReturnType().getName().contains("VideoAdInfo")))
                            .filter(c -> Stream.of(c.getDeclaredMethods()).anyMatch(m -> m.getReturnType() == adClazz))
                            .findFirst()
                            .get();
                } catch (NoSuchElementException e) {
                    // Same visibility rule as BottomTabView: a silent failure here looks like
                    // "ad removal simply does nothing".
                    XposedCompat.log("dolby_beta ClassHelper.Ad: no class matched the pattern");
                    MessageHelper.sendNotification(context, MessageHelper.tabClassNotFoundCode);
                }
            }
            return clazz;
        }

        public static List<Method> getAdMethod(Context context) {
            try {
                List<Method> methodList = Arrays.asList(getClazz(context).getDeclaredMethods());
                List<Method> hookMethodList = Stream.of(methodList)
                        .filter(m -> m.getReturnType().getName().contains("com.netease.cloudmusic.meta"))
                        .filter(m -> Stream.of(m.getParameterTypes()).anyMatch(c -> c == JSONObject.class))
                        .toList();
                hookMethodList.addAll(Stream.of(methodList)
                        .filter(m -> Stream.of(m.getParameterTypes()).anyMatch(c -> c.getName().contains("com.netease.cloudmusic.meta")))
                        .filter(m -> Stream.of(m.getParameterTypes()).anyMatch(c -> c == JSONObject.class))
                        .toList());
                return hookMethodList;
            } catch (Throwable e) {
                XposedCompat.log("dolby_beta ClassHelper.Ad: ad method lookup failed");
                XposedCompat.log(e);
                return null;
            }
        }
    }

    public static class HttpInterceptor {
        private static Class<?> clazz;
        private static List<Method> methodList;

        static Class<?> getClazz(Context context) {
            if (clazz == null) {
                Pattern pattern;
                if (versionCode < 154)
                    pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]");
                else
                    pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]");
                try {
                    List<String> list = ClassHelper.getFilteredClasses(pattern, Collections.reverseOrder());
                    clazz = Stream.of(list)
                            .map(ClassHelper::getClassByXposed)
                            .filter(c -> c.getInterfaces().length == 1)
                            .filter(c -> Stream.of(c.getInterfaces()).anyMatch(i -> i.getName().contains("Interceptor")))
                            .filter(c -> !Modifier.isAbstract(c.getModifiers()))
                            .filter(c -> Modifier.isPublic(c.getModifiers()))
                            .filter(c -> Stream.of(c.getDeclaredMethods()).anyMatch(m -> m.getReturnType().getName().contains("Pair")))
                            .findFirst()
                            .get();
                } catch (Exception e) {
                    MessageHelper.sendNotification(context, MessageHelper.coreClassNotFoundCode);
                }
            }
            return clazz;
        }

        public static List<Method> getMethodList(Context context) {
            if (methodList == null) {
                methodList = new ArrayList<>();
                methodList.addAll(Stream.of(getClazz(context).getDeclaredMethods())
                        .filter(m -> m.getExceptionTypes().length == 1)
                        .filter(m -> m.getParameterTypes().length == 5)
                        .filter(m -> m.getReturnType().getName().contains("Response"))
                        .toList());
            }
            return methodList;
        }
    }
}
