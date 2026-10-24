package com.raincat.dolby_beta.port;

import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PortKit {

    private static final Set<String> sHitLogged = ConcurrentHashMap.newKeySet();

    private PortKit() {
    }

    // lookup
    public static Class<?> findClass(String name, ClassLoader cl) {
        if (name == null || cl == null) {
            return null;
        }
        try {
            Class<?> cls = Class.forName(name, false, cl);
            XposedCompat.logDebug("PortKit class " + name);
            return cls;
        } catch (Throwable t) {
            XposedCompat.logDebug("PortKit skip: class not found " + name);
            return null;
        }
    }

    /**
     * Name-only lookup. Walks the superclass chain and refuses to guess when the declaring class
     * has more than one overload. The previous version only scanned {@code getDeclaredMethods()},
     * so a method inherited from a base class was reported as missing.
     */
    public static Method findMethod(Class<?> cls, String name) {
        if (cls == null || name == null) {
            return null;
        }
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method found = null;
                for (Method m : c.getDeclaredMethods()) {
                    if (!name.equals(m.getName())) {
                        continue;
                    }
                    if (found != null) {
                        XposedCompat.logDebug("PortKit ambiguous method " + cls.getName() + "#" + name);
                        return null;
                    }
                    found = m;
                }
                if (found != null) {
                    found.setAccessible(true);
                    return found;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** Exact-signature lookup walking up the superclass chain. */
    public static Method findMethod(Class<?> cls, String name, Class<?>... params) {
        if (cls == null || name == null) {
            return null;
        }
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * 按参数类型「全名」匹配，不要求该类型在模块编译期可见。
     *
     * 用于 com.alibaba.fastjson.JSONObject 这类宿主自带、模块没有编译期依赖的类型：
     * 直接写 .class 会编译失败，按名字比对即可，命中后一样能正常 hook。
     */
    public static Method findMethodByParamTypeName(Class<?> cls, String name, String paramTypeName) {
        if (cls == null || name == null || paramTypeName == null) {
            return null;
        }
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method found = null;
                for (Method m : c.getDeclaredMethods()) {
                    if (!name.equals(m.getName())) {
                        continue;
                    }
                    Class<?>[] types = m.getParameterTypes();
                    if (types.length != 1 || !paramTypeName.equals(types[0].getName())) {
                        continue;
                    }
                    if (found != null) {
                        // Two overloads with the same parameter type name; picking the first one
                        // silently hooked an arbitrary method.
                        XposedCompat.logDebug("PortKit ambiguous method " + cls.getName() + "#" + name
                                + "(" + paramTypeName + ")");
                        return null;
                    }
                    found = m;
                }
                if (found != null) {
                    found.setAccessible(true);
                    return found;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    // install
    public static boolean install(String label, Method m, MethodHook hook) {
        if (m == null) {
            return false;
        }
        try {
            XposedCompat.hookMethod(m, hook);
            XposedCompat.logInfo("PortKit registered " + label);
            return true;
        } catch (Throwable t) {
            XposedCompat.logError("PortKit hook failed " + label);
            XposedCompat.log(t);
            return false;
        }
    }

    // common hook bodies

    /** Logs the first hit only. */
    public static void hit(String label) {
        if (sHitLogged.add(label)) {
            XposedCompat.logInfo("PortKit hit " + label);
        }
    }

    /** Returns a constant boolean. */
    public static MethodHook constBool(final String label, final boolean value) {
        return new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                hit(label + " -> " + value);
                param.setResult(value);
            }
        };
    }

    /** Skips a void method. */
    public static MethodHook skipVoid(final String label) {
        return new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                hit(label);
                param.setResult(null);
            }
        };
    }

    /** Rejects const-bool hooks on non-boolean returns. */
    public static boolean installBool(String label, Method m, boolean value) {
        if (m == null) {
            return false;
        }
        Class<?> rt = m.getReturnType();
        if (rt != boolean.class && rt != Boolean.class) {
            XposedCompat.logError("PortKit return mismatch " + label + " -> " + rt.getName()
                    + " (constBool requires boolean); skipped");
            return false;
        }
        return install(label, m, constBool(label, value));
    }

    /**
     * Skips a method whose return type is only known at runtime, returning that type's zero value.
     * Reference returns are refused because null could NPE at the call site; Boolean is the one
     * exception and maps to false so an unboxing caller stays safe.
     */
    public static boolean installSkipDefault(String label, Method m) {
        if (m == null) {
            return false;
        }
        Class<?> rt = m.getReturnType();
        if (rt != void.class && !rt.isPrimitive() && rt != Boolean.class) {
            XposedCompat.logError("PortKit skipDefault " + label + " returns " + rt.getName()
                    + "; not skipped");
            return false;
        }
        return install(label, m, new MethodHook() {
            @Override
            protected boolean usesArgs() {
                return false;
            }

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                hit(label);
                param.setResult(zeroValue(((Method) param.method).getReturnType()));
            }
        });
    }

    private static Object zeroValue(Class<?> type) {
        if (type == void.class)
            return null;
        if (type == boolean.class || type == Boolean.class)
            return false;
        if (type == char.class)
            return '\0';
        if (type == long.class)
            return 0L;
        if (type == float.class)
            return 0f;
        if (type == double.class)
            return 0d;
        return 0;
    }
}
