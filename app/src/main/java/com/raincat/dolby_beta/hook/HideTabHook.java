package com.raincat.dolby_beta.hook;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;
import static com.raincat.dolby_beta.xposed.XposedCompat.*;

import android.content.Context;
import android.content.Intent;

import com.raincat.dolby_beta.helper.ClassHelper;
import com.raincat.dolby_beta.helper.DexKitHelper;
import com.raincat.dolby_beta.helper.SettingHelper;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HideTabHook {
    private static boolean loggedNavigationPatch = false;

    public HideTabHook(Context context, int versionCode) {
        if (!SettingHelper.getInstance().isEnable(SettingHelper.beauty_tab_hide_key) || versionCode < 138)
            return;

        if (versionCode >= 9000000)
            hookNavigationTabState(context);

        List<Method> setTabItemMethods = ClassHelper.MainActivitySuperClass.getTabItemStringMethods(context);
        if (setTabItemMethods != null && setTabItemMethods.size() != 0) {
            for (final Method method : setTabItemMethods) {
                hookMethod(method, new MethodHook() {
                    @Override
                    protected void beforeHookedMethod(final MethodHookParam param) {
                        if (param.args[0] == null || ((String[]) param.args[0]).length < 2)
                            return;
                        String[] tabNames = (String[]) param.args[0];
                        String tabName = Arrays.toString(tabNames);
                        // Host CN builds render the tab titles in Chinese, other builds in
                        // English, so both spellings must be matched.
                        if ((tabName.contains("我的") && tabName.contains("发现")) || (tabName.contains("mine") && tabName.contains("main"))) {
                            String[] strings = new String[2];
                            System.arraycopy(tabNames, 0, strings, 0, 2);
                            param.args[0] = strings;
                            XposedCompat.logInfo("HideTabHook: " + method.getName()
                                    + " titles " + tabName + " -> " + Arrays.toString(strings));
                        }
                    }
                });
            }
            XposedCompat.logInfo("HideTabHook: tab title methods installed, count=" + setTabItemMethods.size()
                    + ", superClass=" + ClassHelper.MainActivitySuperClass.getSuperClassName());

            Method viewPagerInitMethod = ClassHelper.MainActivitySuperClass.getViewPagerInitMethod(context);
            if (viewPagerInitMethod != null) {
                hookMethod(viewPagerInitMethod, new MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        Intent intent = (Intent) param.args[0];
                        intent.putExtra("SELECT_PAGE_INDEX", 0);
                    }
                });
            } else {
                XposedCompat.logError("HideTabHook: view pager init method not found");
            }
        } else {
            XposedCompat.logError("HideTabHook: tab title methods not found on MainActivity superclass");
        }

        if (versionCode >= 8000010 && versionCode < 9000000) {
            Class<?> bottomTabViewClass = ClassHelper.BottomTabView.getClazz(context);
            if (bottomTabViewClass != null) {
                findAndHookMethod(bottomTabViewClass, ClassHelper.BottomTabView.getTabInitMethod(context).getName(), new MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        param.setResult(pinnedTabCodes());
                    }
                });

                findAndHookMethod(bottomTabViewClass, ClassHelper.BottomTabView.getTabRefreshMethod(context).getName(), List.class, new MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        param.args[0] = pinnedTabCodes();
                    }
                });
            }
        }
    }

    private static void hookNavigationTabState(Context context) {
        Method updateMethod = findNavigationTabUpdateMethod(context);
        if (updateMethod == null) {
            XposedCompat.logError("HideTabHook: main navigation state method not found");
            return;
        }

        XposedCompat.hookMethod(updateMethod, new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                try {
                    if (!(param.args[0] instanceof List))
                        return;
                    @SuppressWarnings("unchecked")
                    List<Object> original = (List<Object>) param.args[0];
                    if (original == null || original.isEmpty())
                        return;

                    boolean hasMine = false;
                    boolean hasMain = false;
                    List<Object> filtered = new ArrayList<>();
                    for (Object item : original) {
                        if (item == null)
                            continue;
                        Object tabCodeObject = callMethod(item, "getTabCode");
                        String tabCode = tabCodeObject == null ? "" : tabCodeObject.toString();
                        if ("mine".equals(tabCode)) {
                            hasMine = true;
                            filtered.add(item);
                        } else if ("main".equals(tabCode)) {
                            hasMain = true;
                            filtered.add(item);
                        }
                    }
                    if (!hasMine || !hasMain) {
                        if (!loggedNavigationPatch) {
                            XposedCompat.logInfo("HideTabHook: navigation tab list ignored, mine=" + hasMine
                                    + ", main=" + hasMain + ", size=" + original.size());
                            loggedNavigationPatch = true;
                        }
                        return;
                    }

                    param.args[0] = filtered;
                    if (!loggedNavigationPatch) {
                        XposedCompat.logInfo("HideTabHook: navigation tabs filtered, " + original.size()
                                + " -> " + filtered.size() + ", method=" + updateMethod);
                        loggedNavigationPatch = true;
                    }
                } catch (Throwable t) {
                    XposedCompat.log(t);
                }
            }
        });
        XposedCompat.logInfo("HideTabHook: navigation state hook installed, method=" + updateMethod);
    }

    private static Method findNavigationTabUpdateMethod(Context context) {
        for (String className : DexKitHelper.findMainNavigationViewModelCandidates(context)) {
            Class<?> clazz = findClassIfExists(className, context.getClassLoader());
            if (clazz == null)
                continue;

            List<Class<?>> searchClasses = new ArrayList<>();
            searchClasses.add(clazz);
            searchClasses.addAll(Arrays.asList(clazz.getDeclaredClasses()));
            for (Class<?> searchClass : searchClasses) {
                for (Method method : searchClass.getDeclaredMethods()) {
                    Class<?>[] types = method.getParameterTypes();
                    if (types.length == 2
                            && types[0] == List.class
                            && types[1] == boolean.class
                            && method.getReturnType() == void.class) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    /** Tab codes kept by the tab-hiding feature; a fresh list per call. */
    private static List<String> pinnedTabCodes() {
        List<String> list = new ArrayList<>();
        list.add("mine");
        list.add("main");
        list.add("follow");
        return list;
    }
}
