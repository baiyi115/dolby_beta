package com.raincat.dolby_beta.xposed;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/** The only place in the module that talks to libxposed API 102. */
public class XposedCompat {

    private static final String TAG = "dolby_beta";
    private static volatile XposedInterface xp;
    private static volatile String moduleApkPath;
    private static final java.util.Map<String, int[]> dupCounter = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int DUP_COUNTER_LIMIT = 512;
    private static final java.util.concurrent.atomic.AtomicInteger hookInstalled = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.List<String> hookFailures = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /**
     * Member resolution per (class, name, runtime arg types) is stable, so memoize it: hot hooks
     * (once per HTTP request, once per trial-state getter) must not re-scan the class hierarchy
     * on every call.
     */
    private static final java.util.concurrent.ConcurrentHashMap<String, Method> METHOD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Field> FIELD_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void init(XposedInterface instance) {
        xp = instance;
        try {
            moduleApkPath = instance.getModuleApplicationInfo().sourceDir;
        } catch (Throwable ignored) {
        }
    }

    public static String getModuleApkPath() {
        return moduleApkPath;
    }

    public static boolean isReady() {
        return xp != null;
    }

    // ------------------------------------------------------------------ logging

    public static void logInfo(String message) {
        XposedInterface i = xp;
        if (i != null)
            i.log(Log.INFO, TAG, message);
        else
            Log.i(TAG, message);
    }

    public static void logError(String message) {
        XposedInterface i = xp;
        if (i != null)
            i.log(Log.ERROR, TAG, message);
        else
            Log.e(TAG, message);
    }

    /**
     * Tracing stays off unless Hook turns it on from the debug marker file, so a device build can
     * be bisected without a rebuild. Milestones use logInfo(); failures use log()/log(Throwable)
     * so they always reach the LSPosed log with a full stack.
     */
    private static volatile boolean debug;

    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void logDebug(String message) {
        if (debug)
            logInfo("[debug] " + message);
    }

    public static void log(String message) {
        XposedInterface i = xp;
        int[] c = dupCounter.get(message);
        if (c == null) {
            // Bounded on purpose: stack-trace strings are used as keys and behave like unique
            // values, so the map previously grew for the whole process lifetime.
            if (dupCounter.size() >= DUP_COUNTER_LIMIT)
                dupCounter.clear();
            dupCounter.put(message, new int[]{1});
            if (i != null)
                i.log(Log.ERROR, TAG, message);
            else
                Log.e(TAG, message);
        } else if (++c[0] % 50 == 1) {
            if (i != null)
                i.log(Log.ERROR, TAG, message + " (x" + c[0] + ")");
            else
                Log.e(TAG, message + " (x" + c[0] + ")");
        }
    }

    public static void log(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        log(sw.toString());
    }

    public static void noteHookInstalled() {
        hookInstalled.incrementAndGet();
    }

    public static void noteHookFailed(String what, Throwable t) {
        hookFailures.add(what + " -> " + t);
        log("hook FAILED: " + what + " -> " + t);
        log(t);
    }

    public static void logSummary(String phase) {
        logInfo("[" + phase + "] hooks installed=" + hookInstalled.get()
                + ", failed=" + hookFailures.size()
                + (hookFailures.isEmpty() ? "" : " details=" + hookFailures));
    }

    // ------------------------------------------------------------------ hooking

    public static void hookMethod(Member member, MethodHook hook) {
        if (!(member instanceof Executable)) {
            log("cannot hook non-executable: " + member);
            return;
        }
        Executable exec = (Executable) member;
        try {
            exec.setAccessible(true);
            XposedInterface i = xp;
            if (i == null)
                throw new IllegalStateException("XposedCompat not initialized");
            i.hook(exec).intercept(chain -> MethodHook.dispatch(hook, exec, chain));
            noteHookInstalled();
        } catch (Throwable t) {
            noteHookFailed("hook " + exec, t);
        }
    }

    public static void hookAllMethods(Class<?> clazz, String methodName, MethodHook hook) {
        try {
            for (Method method : clazz.getDeclaredMethods())
                if (method.getName().equals(methodName))
                    hookMethod(method, hook);
        } catch (Throwable t) {
            log(t);
        }
    }

    public static void hookAllConstructors(Class<?> clazz, MethodHook hook) {
        try {
            for (Constructor<?> ctor : clazz.getDeclaredConstructors())
                hookMethod(ctor, hook);
        } catch (Throwable t) {
            log(t);
        }
    }

    public static void findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) {
        hookMethod(findMethodExact(clazz, methodName, parameterTypesAndCallback), extractHook(parameterTypesAndCallback));
    }

    public static void findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) {
        findAndHookMethod(findClass(className, classLoader), methodName, parameterTypesAndCallback);
    }

    public static void findAndHookConstructor(Class<?> clazz, Object... parameterTypesAndCallback) {
        hookMethod(findConstructorExact(clazz, parameterTypesAndCallback), extractHook(parameterTypesAndCallback));
    }

    public static void findAndHookConstructor(String className, ClassLoader classLoader, Object... parameterTypesAndCallback) {
        findAndHookConstructor(findClass(className, classLoader), parameterTypesAndCallback);
    }

    // ------------------------------------------------------------------ class loading

    public static Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundError(className, e);
        }
    }

    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ reflection helpers

    public static Object callMethod(Object obj, String methodName, Object... args) {
        try {
            Method method = findMethodBestMatch(obj.getClass(), methodName, args);
            return method.invoke(obj, args);
        } catch (Throwable t) {
            log("callMethod " + methodName + " on " + obj.getClass().getName() + " failed: " + t);
            log(t);
            return null;
        }
    }

    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) {
        try {
            Method method = findMethodBestMatch(clazz, methodName, args);
            return method.invoke(null, args);
        } catch (Throwable t) {
            log("callStaticMethod " + clazz.getName() + "#" + methodName + " failed: " + t);
            log(t);
            return null;
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        String key = clazz.getName() + '#' + fieldName;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null)
            return cached;
        Field resolved = resolveField(clazz, fieldName);
        Field previous = FIELD_CACHE.putIfAbsent(key, resolved);
        return previous != null ? previous : resolved;
    }

    private static Field resolveField(Class<?> clazz, String fieldName) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields())
                if (field.getName().equals(fieldName)) {
                    field.setAccessible(true);
                    return field;
                }
        }
        throw new NoSuchFieldError(clazz.getName() + "#" + fieldName);
    }

    public static Object getObjectField(Object obj, String fieldName) {
        try {
            Field field = findField(obj.getClass(), fieldName);
            return field.get(obj);
        } catch (Throwable t) {
            log("getObjectField " + fieldName + " on " + obj.getClass().getName() + " failed: " + t);
            log(t);
            return null;
        }
    }

    public static Object newInstance(Class<?> clazz, Object... args) {
        try {
            Constructor<?> ctor = findConstructorBestMatch(clazz, args);
            return ctor.newInstance(args);
        } catch (Throwable t) {
            log("newInstance " + clazz.getName() + " failed: " + t);
            log(t);
            return null;
        }
    }

    public static Method[] findMethodsByExactParameters(Class<?> clazz, Class<?> returnType, Class<?>... parameterTypes) {
        Set<Method> out = new HashSet<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (returnType != null && !returnType.isAssignableFrom(method.getReturnType()))
                    continue;
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length != parameterTypes.length)
                    continue;
                boolean ok = true;
                for (int i = 0; i < paramTypes.length; i++)
                    if (paramTypes[i] != parameterTypes[i]) {
                        ok = false;
                        break;
                    }
                if (ok) {
                    method.setAccessible(true);
                    out.add(method);
                }
            }
        }
        return out.toArray(new Method[0]);
    }

    // ------------------------------------------------------------------ resolution internals

    private static MethodHook extractHook(Object[] parameterTypesAndCallback) {
        for (Object o : parameterTypesAndCallback)
            if (o instanceof MethodHook)
                return (MethodHook) o;
        throw new IllegalArgumentException("no MethodHook in parameter list");
    }

    private static Executable findMethodExact(Class<?> clazz, String methodName, Object[] parameterTypesAndCallback) {
        Class<?>[] paramTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < paramTypes.length; i++) {
            Object o = parameterTypesAndCallback[i];
            try {
                paramTypes[i] = o instanceof Class ? (Class<?>) o
                        : Class.forName((String) o, false, clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new ClassNotFoundError(String.valueOf(o), e);
            }
        }
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods())
                if (method.getName().equals(methodName) && exactParams(method.getParameterTypes(), paramTypes)) {
                    method.setAccessible(true);
                    return method;
                }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName + toStr(paramTypes));
    }

    private static Constructor<?> findConstructorExact(Class<?> clazz, Object[] parameterTypesAndCallback) {
        Class<?>[] paramTypes = new Class<?>[parameterTypesAndCallback.length - 1];
        for (int i = 0; i < paramTypes.length; i++) {
            Object o = parameterTypesAndCallback[i];
            try {
                paramTypes[i] = o instanceof Class ? (Class<?>) o
                        : Class.forName((String) o, false, clazz.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new ClassNotFoundError(String.valueOf(o), e);
            }
        }
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Constructor<?> ctor : c.getDeclaredConstructors())
                if (exactParams(ctor.getParameterTypes(), paramTypes)) {
                    ctor.setAccessible(true);
                    return ctor;
                }
        }
        throw new NoSuchMethodError(clazz.getName() + "<init>" + toStr(paramTypes));
    }

    private static Method findMethodBestMatch(Class<?> clazz, String methodName, Object[] args) {
        String key = cacheKey(clazz, methodName, args);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null)
            return cached;
        Method resolved = resolveMethodBestMatch(clazz, methodName, args);
        Method previous = METHOD_CACHE.putIfAbsent(key, resolved);
        return previous != null ? previous : resolved;
    }

    /** Runtime argument classes fully determine the best-match result, so they form the key. */
    private static String cacheKey(Class<?> clazz, String methodName, Object[] args) {
        StringBuilder key = new StringBuilder(clazz.getName()).append('#').append(methodName).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0)
                key.append(',');
            key.append(args[i] == null ? "null" : args[i].getClass().getName());
        }
        return key.append(')').toString();
    }

    private static Method resolveMethodBestMatch(Class<?> clazz, String methodName, Object[] args) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            Method best = null;
            int bestScore = Integer.MAX_VALUE;
            for (Method method : c.getDeclaredMethods()) {
                if (!method.getName().equals(methodName))
                    continue;
                Class<?>[] paramTypes = method.getParameterTypes();
                if (paramTypes.length != args.length)
                    continue;
                int score = assignScore(paramTypes, args);
                if (score >= 0 && score < bestScore) {
                    best = method;
                    bestScore = score;
                }
            }
            if (best != null) {
                best.setAccessible(true);
                return best;
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "#" + methodName + " args=" + args.length);
    }

    private static Constructor<?> findConstructorBestMatch(Class<?> clazz, Object[] args) {
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            Constructor<?> best = null;
            int bestScore = Integer.MAX_VALUE;
            for (Constructor<?> ctor : c.getDeclaredConstructors()) {
                Class<?>[] paramTypes = ctor.getParameterTypes();
                if (paramTypes.length != args.length)
                    continue;
                int score = assignScore(paramTypes, args);
                if (score >= 0 && score < bestScore) {
                    best = ctor;
                    bestScore = score;
                }
            }
            if (best != null) {
                best.setAccessible(true);
                return best;
            }
        }
        throw new NoSuchMethodError(clazz.getName() + "<init> args=" + args.length);
    }

    private static boolean exactParams(Class<?>[] a, Class<?>[] b) {
        if (a.length != b.length)
            return false;
        for (int i = 0; i < a.length; i++)
            if (a[i] != b[i])
                return false;
        return true;
    }

    private static int assignScore(Class<?>[] paramTypes, Object[] args) {
        int score = 0;
        for (int i = 0; i < paramTypes.length; i++) {
            Object arg = args[i];
            Class<?> p = paramTypes[i];
            if (arg == null) {
                if (p.isPrimitive())
                    return -1;
                continue;
            }
            Class<?> a = arg.getClass();
            Class<?> boxed = p;
            if (p.isPrimitive()) {
                boxed = PRIMITIVES.get(p);
                if (boxed == null)
                    return -1;
                score += 2;
            }
            if (boxed == a)
                continue;
            if (!boxed.isAssignableFrom(a))
                return -1;
            score += 1;
        }
        return score;
    }

    private static final Map<Class<?>, Class<?>> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put(int.class, Integer.class);
        PRIMITIVES.put(long.class, Long.class);
        PRIMITIVES.put(boolean.class, Boolean.class);
        PRIMITIVES.put(byte.class, Byte.class);
        PRIMITIVES.put(short.class, Short.class);
        PRIMITIVES.put(char.class, Character.class);
        PRIMITIVES.put(float.class, Float.class);
        PRIMITIVES.put(double.class, Double.class);
    }

    private static String toStr(Class<?>[] types) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> t : types)
            sb.append(t.getName()).append(',');
        return sb.append(')').toString();
    }

    public static class ClassNotFoundError extends Error {
        public ClassNotFoundError(String name, Throwable cause) {
            super("Class not found: " + name, cause);
        }
    }
}
