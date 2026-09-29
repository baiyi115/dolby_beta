# Add project specific ProGuard rules here.
# Note: minifyEnabled is false in app/build.gradle, so nothing here runs today. The rules are kept
# accurate so that turning minification on does not require rediscovering what must survive.

# The libxposed entry point is resolved by name from META-INF/xposed/java_init.list.
-keep class com.raincat.dolby_beta.HookEntry
-keep class com.raincat.dolby_beta.helper.ScriptHelper

# JSON models are filled in reflectively by gson.
-keep public class **.*model*.** {*;}

-dontwarn sun.misc.Unsafe
-dontwarn com.google.common.collect.MinMaxPriorityQueue
-dontwarn com.google.common.util.concurrent.FuturesGetChecked**
-dontwarn javax.lang.model.element.Modifier
-dontwarn afu.org.checkerframework.**
-dontwarn org.checkerframework.**
-dontwarn android.app.**
-dontwarn org.jf.dexlib2.dexbacked.**

# 删除日志
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int d(...);
    public static int w(...);
    public static int v(...);
    public static int i(...);
}
