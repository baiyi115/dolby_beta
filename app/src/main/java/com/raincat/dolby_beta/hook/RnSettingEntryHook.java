package com.raincat.dolby_beta.hook;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.raincat.dolby_beta.BuildConfig;
import com.raincat.dolby_beta.helper.DexKitHelper;
import com.raincat.dolby_beta.xposed.MethodHook;
import com.raincat.dolby_beta.xposed.XposedCompat;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import static com.raincat.dolby_beta.xposed.XposedCompat.findClassIfExists;

public class RnSettingEntryHook {
    private static final String RN_CONTAINER_CLASS = "om1.w";
    private static final String RN_ACTIVITY_CLASS =
            "com.netease.cloudmusic.music.biz.rn.activity.MainProcessRNActivity";
    private static final String ENTRY_TAG = "dolby_beta_rn_setting_entry_item";
    private static final String ARROW_TAG = "dolby_beta_rn_setting_entry_arrow";
    private static final String ACCOUNT_ROW_TEXT = "账号与安全";
    private static final int SECTION_GAP_DP = 14;
    private static final int MAX_RENDER_WAIT_TIMES = 30;
    private static final long RENDER_WAIT_INTERVAL_MS = 200L;
    private static final Set<String> REPORTED_MODULE_NAMES =
            Collections.synchronizedSet(new HashSet<>());
    private static final Map<Activity, ViewTreeObserver.OnGlobalLayoutListener> LAYOUT_WATCHERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** View each layout watcher was attached to, so it can be unregistered after the view is gone. */
    private static final Map<Activity, WeakReference<View>> LAYOUT_WATCHER_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());

    public RnSettingEntryHook(Context context) {
        Class<?> containerClass = findClassIfExists(RN_CONTAINER_CLASS, context.getClassLoader());
        Method initMethod = findContainerInitMethod(containerClass);

        // RN_CONTAINER_CLASS is a hard-coded obfuscated name, so it is only right for the NetEase
        // build it was found in. Fall back to DexKit whenever the class is missing *or* still
        // exists but no longer has a (Bundle, ViewGroup) init method: the previous check only
        // covered the missing-class case, and a mere signature change silently removed the entry.
        if (initMethod == null && containerClass != null)
            XposedCompat.log("dolby_beta RnSettingEntryHook: " + RN_CONTAINER_CLASS
                    + " init method shape changed, trying DexKit");
        if (initMethod == null) {
            for (String name : DexKitHelper.findRnContainerCandidates(context)) {
                Class<?> candidate = findClassIfExists(name, context.getClassLoader());
                Method candidateInit = findContainerInitMethod(candidate);
                if (candidateInit != null) {
                    containerClass = candidate;
                    initMethod = candidateInit;
                    XposedCompat.logInfo("dolby_beta RnSettingEntryHook: DexKit fallback=" + name);
                    break;
                }
            }
        }

        if (initMethod == null) {
            XposedCompat.logError("dolby_beta RnSettingEntryHook: init method not found"
                    + ", container=" + (containerClass == null ? "null" : containerClass.getName())
                    + ", target=" + context.getPackageName());
            return;
        }

        debug("hook installing, containerClass=" + containerClass.getName());
        hookContainerInit(initMethod);
        hookContainerDestroy(initMethod.getDeclaringClass());
        hookSettingsActivityLifecycle(context.getClassLoader());
        debug("hook installed, method=" + initMethod);
    }

    private static void hookContainerInit(Method method) {
        XposedCompat.hookMethod(method, new MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                try {
                    handleContainerStarted(param.thisObject);
                } catch (Throwable t) {
                    XposedCompat.noteHookFailed("RnSettingEntryHook#init", t);
                }
            }
        });
    }

    private static void hookContainerDestroy(Class<?> containerClass) {
        try {
            XposedCompat.findAndHookMethod(containerClass, "n", new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    try {
                        handleContainerDestroyed(param.thisObject);
                    } catch (Throwable t) {
                        XposedCompat.noteHookFailed("RnSettingEntryHook#destroy", t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("RnSettingEntryHook#hook n", t);
        }
    }

    private static void hookSettingsActivityLifecycle(ClassLoader classLoader) {
        Class<?> activityClass = findClassIfExists(RN_ACTIVITY_CLASS, classLoader);
        if (activityClass == null) {
            XposedCompat.logError("RnSettingEntryHook: MainProcessRNActivity not found");
            return;
        }
        try {
            XposedCompat.findAndHookMethod(activityClass, "onResume", new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    try {
                        handleActivityResumed((Activity) param.thisObject);
                    } catch (Throwable t) {
                        XposedCompat.noteHookFailed("RnSettingEntryHook#activityResume", t);
                    }
                }
            });
            XposedCompat.findAndHookMethod(activityClass, "onDestroy", new MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    super.afterHookedMethod(param);
                    try {
                        removeEntry((Activity) param.thisObject);
                        SettingHook.releaseFromEntry((Activity) param.thisObject);
                    } catch (Throwable t) {
                        XposedCompat.noteHookFailed("RnSettingEntryHook#activityDestroy", t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedCompat.noteHookFailed("RnSettingEntryHook#activityLifecycle", t);
        }
    }

    private static void handleActivityResumed(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed())
            return;
        Intent intent = activity.getIntent();
        String moduleName = intent == null ? null : intent.getStringExtra("extra_module_name");
        if (!isSettingsModule(moduleName)) {
            removeEntry(activity);
            SettingHook.releaseFromEntry(activity);
            return;
        }
        debug("activity resume schedule inject, module=" + moduleName
                + ", activity=" + activity.getClass().getName());
        scheduleInject(activity, 0);
    }

    private static Method findContainerInitMethod(Class<?> clazz) {
        if (clazz == null) return null;
        for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getReturnType() != void.class || !Modifier.isPublic(method.getModifiers())) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 2 && types[0] == Bundle.class && types[1] == ViewGroup.class)
                    return method;
            }
        }
        return null;
    }

    private static void handleContainerStarted(Object container) {
        String moduleName = getModuleName(container);
        debug("init hook triggered, module=" + moduleName
                + ", container=" + (container == null ? "null" : container.getClass().getName()));
        if (!isSettingsModule(moduleName)) {
            reportUnknownModuleOnce(moduleName, container);
            return;
        }

        Object activityObject = getActivity(container);
        if (!(activityObject instanceof Activity)) {
            XposedCompat.logError("RnSettingEntryHook: settings activity not found, module=" + moduleName);
            return;
        }
        Activity activity = (Activity) activityObject;
        if (activity.isFinishing() || activity.isDestroyed()) return;

        debug("schedule inject, module=" + moduleName + ", activity=" + activity.getClass().getName());
        scheduleInject(activity, 0);
    }

    private static void handleContainerDestroyed(Object container) {
        String moduleName = getModuleName(container);
        if (!isSettingsModule(moduleName)) return;
        Object activityObject = getActivity(container);
        if (!(activityObject instanceof Activity)) return;
        Activity activity = (Activity) activityObject;
        removeEntry(activity);
        SettingHook.releaseFromEntry(activity);
    }

    private static String getModuleName(Object container) {
        if (container == null) return null;
        Object direct = invokeNoArg(container, "Q", "getMModuleName");
        if (direct instanceof String) return (String) direct;
        for (Class<?> type = container.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.getParameterTypes().length != 0
                        || method.getReturnType() != String.class) continue;
                Object value = invoke(method, container);
                if (value instanceof String) return (String) value;
            }
        }
        return null;
    }

    private static Object getActivity(Object container) {
        if (container == null) return null;
        Object direct = invokeNoArg(container, "N", "getMActivity");
        if (direct instanceof Activity) return direct;
        for (Class<?> type = container.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        || method.getParameterTypes().length != 0
                        || !Activity.class.isAssignableFrom(method.getReturnType())) continue;
                Object value = invoke(method, container);
                if (value instanceof Activity) return value;
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null || names == null) return null;
        for (String name : names) {
            for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals(name) || method.getParameterTypes().length != 0) continue;
                    Object value = invoke(method, target);
                    if (value != null) return value;
                }
            }
        }
        return null;
    }

    private static Object invoke(Method method, Object target) {
        try {
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isSettingsModule(String moduleName) {
        return "rn-setting".equals(moduleName) || "rn-setting@index".equals(moduleName);
    }

    private static void scheduleInject(final Activity activity, final int attempt) {
        if (attempt > MAX_RENDER_WAIT_TIMES || activity.isFinishing() || activity.isDestroyed()) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            if (attempt == MAX_RENDER_WAIT_TIMES)
                XposedCompat.logError("RnSettingEntryHook: android.R.id.content not found");
            return;
        }
        content.postDelayed(() -> {
            try {
                injectSettingEntry(activity, attempt);
            } catch (Throwable t) {
                XposedCompat.noteHookFailed("RnSettingEntryHook#inject", t);
            }
        }, RENDER_WAIT_INTERVAL_MS);
    }

    private static void injectSettingEntry(Activity activity, int attempt) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            XposedCompat.logError("RnSettingEntryHook: android.R.id.content not found");
            return;
        }
        if (findEntry(content, 0) != null) {
            debug("entry already injected, attempt=" + attempt);
            return;
        }

        TextView accountText = findTextView(content, 0);
        if (accountText == null) {
            debug("account row not rendered, attempt=" + attempt);
            if (attempt < MAX_RENDER_WAIT_TIMES) scheduleInject(activity, attempt + 1);
            else XposedCompat.logError("RnSettingEntryHook: account row not found: " + ACCOUNT_ROW_TEXT);
            return;
        }
        if (!(accountText.getParent() instanceof ViewGroup)) {
            XposedCompat.logError("RnSettingEntryHook: account text parent is not a ViewGroup");
            return;
        }

        ListPlacement placement = findListPlacement(accountText);
        if (placement == null) {
            XposedCompat.logError("RnSettingEntryHook: setting list placement not found");
            return;
        }
        ViewGroup accountRow = placement.row;
        ViewGroup listContainer = placement.list;
        int index = placement.index;
        if (index < 0) {
            XposedCompat.logError("RnSettingEntryHook: account row removed from list container");
            return;
        }

        View entry = createEntry(activity, accountText, accountRow);
        entry.setTag(ENTRY_TAG);
        listContainer.addView(entry, index, new ViewGroup.LayoutParams(
                accountRow.getWidth(), Math.max(accountRow.getHeight(), 1)));
        layoutSettingList(listContainer, accountRow, entry);
        installLayoutWatcher(activity, content, entry);

        XposedCompat.logInfo("RnSettingEntryHook entry injected into list, container="
                + listContainer.getClass().getName() + ", index=" + index
                + ", row=" + accountRow.getClass().getName()
                + ", attempt=" + attempt
                + ", bounds=" + bounds(entry) + ", referenceBounds=" + bounds(accountRow));
    }

    private static final Map<Activity, WeakReference<View>> INJECTED_ENTRIES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Activity, Long> LAST_RECHECK =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Bounds the expensive view-tree walk when a page relayouts faster than we can inject. */
    private static final long RECHECK_INTERVAL_MS = 200L;

    private static View injectedEntry(Activity activity) {
        WeakReference<View> ref = INJECTED_ENTRIES.get(activity);
        return ref == null ? null : ref.get();
    }

    /** RN relayouts drop the injected native view, so the entry is re-installed on global layout. */
    private static void installLayoutWatcher(final Activity activity, View observedView, View entry) {
        if (activity == null || observedView == null || LAYOUT_WATCHERS.containsKey(activity))
            return;
        INJECTED_ENTRIES.put(activity, new WeakReference<>(entry));
        final WeakReference<Activity> activityRef = new WeakReference<>(activity);
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            try {
                Activity currentActivity = activityRef.get();
                if (currentActivity == null
                        || currentActivity.isFinishing()
                        || currentActivity.isDestroyed())
                    return;
                // Cheap path: while our entry is still attached there is nothing to walk for.
                View injected = injectedEntry(currentActivity);
                if (injected != null && injected.isAttachedToWindow())
                    return;
                long now = System.currentTimeMillis();
                Long last = LAST_RECHECK.get(currentActivity);
                if (last != null && now - last < RECHECK_INTERVAL_MS)
                    return;
                LAST_RECHECK.put(currentActivity, now);
                if (tryInjectSettingEntry(currentActivity))
                    XposedCompat.logInfo("RnSettingEntryHook entry restored after RN relayout");
            } catch (Throwable t) {
                XposedCompat.noteHookFailed("RnSettingEntryHook#relayout", t);
            }
        };
        observedView.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        LAYOUT_WATCHERS.put(activity, listener);
        LAYOUT_WATCHER_VIEWS.put(activity, new WeakReference<>(observedView));
    }

    private static boolean tryInjectSettingEntry(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null)
            return false;
        TextView accountText = findTextView(content, 0);
        if (accountText == null || !(accountText.getParent() instanceof ViewGroup))
            return false;
        ListPlacement placement = findListPlacement(accountText);
        if (placement == null)
            return false;
        ViewGroup accountRow = placement.row;
        ViewGroup listContainer = placement.list;
        int index = placement.index;
        if (index < 0)
            return false;
        View entry = findEntry(content, 0);
        if (entry != null) {
            INJECTED_ENTRIES.put(activity, new WeakReference<>(entry));
            forceEntryLayout(entry, accountRow);
            return false;
        }
        View newEntry = createEntry(activity, accountText, accountRow);
        newEntry.setTag(ENTRY_TAG);
        listContainer.addView(newEntry, index, new ViewGroup.LayoutParams(
                accountRow.getWidth(), Math.max(accountRow.getHeight(), 1)));
        layoutSettingList(listContainer, accountRow, newEntry);
        INJECTED_ENTRIES.put(activity, new WeakReference<>(newEntry));
        return true;
    }

    private static ListPlacement findListPlacement(TextView accountText) {
        if (accountText == null || !(accountText.getParent() instanceof ViewGroup))
            return null;

        ViewGroup child = (ViewGroup) accountText.getParent();
        while (child != null) {
            if (!(child.getParent() instanceof ViewGroup))
                return null;
            ViewGroup parent = (ViewGroup) child.getParent();
            // The account row is wrapped in several nested RN containers; the real settings list
            // is the nearest ancestor that also holds the next section header below it.
            if (parent.getChildCount() > 1 && containsTextNode(parent, "播放与下载"))
                return new ListPlacement(child, parent, parent.indexOfChild(child));
            child = parent;
        }
        return null;
    }

    private static boolean containsTextNode(ViewGroup parent, String expected) {
        if (parent == null)
            return false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof TextView
                    && expected.contentEquals(((TextView) child).getText()))
                return true;
            if (child instanceof ViewGroup && containsTextNode((ViewGroup) child, expected))
                return true;
        }
        return false;
    }

    private static void layoutSettingList(ViewGroup list, ViewGroup accountRow, View entry) {
        if (list == null || accountRow == null || entry == null)
            return;

        int height = Math.max(Math.max(accountRow.getHeight(), accountRow.getMeasuredHeight()), 1);
        int gap = sectionGap(list);

        // ReactViewGroup#onLayout/#requestLayout are no-ops for RN children, so the injected
        // native views must be measured and laid out explicitly. Offsets are applied with
        // translationY, which Yoga does not reset on the next layout pass.
        forceEntryBounds(entry, accountRow, Math.max(0, accountRow.getTop() - height));
        int entryIndex = list.indexOfChild(entry);
        int accountIndex = list.indexOfChild(accountRow);
        if (entryIndex < 0 || accountIndex < 0)
            return;

        int accountVisualTop = accountRow.getTop() + Math.round(accountRow.getTranslationY());
        int desiredAccountTop = entry.getBottom() + gap;
        int shift = desiredAccountTop - accountVisualTop;
        if (shift == 0)
            return;

        for (int i = entryIndex + 1; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child == null || child == entry)
                continue;
            child.setTranslationY(child.getTranslationY() + shift);
        }
        list.layout(list.getLeft(), list.getTop(), list.getRight(), list.getBottom() + Math.max(shift, gap));

        if (entry instanceof ViewGroup) {
            ensureArrow((ViewGroup) entry, accountRow);
            entry.postDelayed(() -> ensureArrow((ViewGroup) entry, accountRow), 80L);
        }
    }

    private static void forceEntryBounds(View entry, View referenceRow, int top) {
        int width = referenceRow.getWidth();
        if (width <= 0)
            width = referenceRow.getMeasuredWidth();
        if (width <= 0)
            width = Math.max(referenceRow.getContext().getResources().getDisplayMetrics().widthPixels, 1);

        entry.forceLayout();
        entry.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(Math.max(referenceRow.getHeight(), 1), View.MeasureSpec.EXACTLY));
        entry.layout(referenceRow.getLeft(), top, referenceRow.getLeft() + width,
                top + Math.max(referenceRow.getHeight(), 1));
        entry.setVisibility(View.VISIBLE);
        entry.setAlpha(1f);
        if (entry instanceof ViewGroup) {
            ViewGroup entryGroup = (ViewGroup) entry;
            ensureArrow(entryGroup, referenceRow);
        }
        if (entry.getLayoutParams() != null) {
            entry.getLayoutParams().width = width;
            entry.getLayoutParams().height = Math.max(referenceRow.getHeight(), 1);
        }
    }

    private static int sectionGap(View view) {
        if (view == null) return 0;
        return Math.max(1, Math.round(SECTION_GAP_DP
                * view.getResources().getDisplayMetrics().density));
    }

    private static void forceEntryLayout(View entry, View referenceRow) {
        if (entry == null || referenceRow == null)
            return;
        int width = referenceRow.getWidth();
        if (width <= 0)
            width = referenceRow.getMeasuredWidth();
        if (width <= 0)
            width = Math.max(referenceRow.getContext().getResources().getDisplayMetrics().widthPixels, 1);

        int height = referenceRow.getHeight();
        if (height <= 0)
            height = referenceRow.getMeasuredHeight();
        if (height <= 0)
            height = Math.max((int) (referenceRow.getContext().getResources()
                    .getDisplayMetrics().density * 56), 1);

        entry.forceLayout();
        entry.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));

        int left = Math.max(0, referenceRow.getLeft());
        int top = Math.max(0, referenceRow.getTop() - height
                - (referenceRow.getTop() > height ? sectionGap(referenceRow) : 0));
        entry.layout(left, top, left + width, top + height);
        entry.setVisibility(View.VISIBLE);
        entry.setAlpha(1f);
        if (entry.getLayoutParams() != null) {
            entry.getLayoutParams().width = width;
            entry.getLayoutParams().height = height;
        }
    }

    private static View createEntry(Activity activity, TextView accountText, ViewGroup referenceRow) {
        debugRowStyle(referenceRow);
        int[] textLocation = new int[2];
        int[] rowLocation = new int[2];
        accountText.getLocationOnScreen(textLocation);
        referenceRow.getLocationOnScreen(rowLocation);
        int startInset = Math.max(0, textLocation[0] - rowLocation[0]);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPaddingRelative(
                Math.max(referenceRow.getPaddingStart(), startInset), referenceRow.getPaddingTop(),
                referenceRow.getPaddingEnd(), referenceRow.getPaddingBottom());
        Drawable background = copyRowBackground(referenceRow, activity);
        if (background != null)
            row.setBackground(background);
        row.setMinimumHeight(referenceRow.getMinimumHeight());

        TextView title = new TextView(activity);
        title.setText("杜比大喇叭β");

        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, accountText.getTextSize());
        title.setTypeface(accountText.getTypeface());
        title.setLetterSpacing(accountText.getLetterSpacing());
        title.setIncludeFontPadding(accountText.getIncludeFontPadding());
        title.setLineSpacing(accountText.getLineSpacingExtra(), accountText.getLineSpacingMultiplier());

        title.setPaddingRelative(
                accountText.getPaddingStart(), accountText.getPaddingTop(),
                accountText.getPaddingEnd(), accountText.getPaddingBottom());

        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (accountText.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams source = (ViewGroup.MarginLayoutParams) accountText.getLayoutParams();
            textParams.leftMargin = source.leftMargin;
            textParams.topMargin = source.topMargin;
            textParams.rightMargin = source.rightMargin;
            textParams.bottomMargin = source.bottomMargin;
        }
        row.addView(title, textParams);

        ImageView arrow = findArrowView(referenceRow, 0);
        if (arrow != null && arrow.getDrawable() != null
                && arrow.getDrawable().getConstantState() != null) {
            ImageView copiedArrow = new ImageView(activity);
            copiedArrow.setImageDrawable(arrow.getDrawable().getConstantState()
                    .newDrawable(activity.getResources()));
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                    Math.max(arrow.getWidth(), 1), Math.max(arrow.getHeight(), 1));
            if (arrow.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams source =
                        (ViewGroup.MarginLayoutParams) arrow.getLayoutParams();
                arrowParams.leftMargin = source.leftMargin;
                arrowParams.topMargin = source.topMargin;
                arrowParams.rightMargin = source.rightMargin;
                arrowParams.bottomMargin = source.bottomMargin;
            }
            row.addView(copiedArrow, arrowParams);
        }

        row.setOnClickListener(view -> {
            debug("entry clicked, activity=" + activity.getClass().getName());
            SettingHook.showFromEntry(activity);
        });

        row.postDelayed(() -> ensureArrow(row, referenceRow), 80L);
        return row;
    }

    private static void debugRowStyle(View row) {
        if (row == null)
            return;
        StringBuilder sb = new StringBuilder("row style:");
        View current = row;
        for (int depth = 0; depth < 15 && current != null; depth++) {
            Drawable background = current.getBackground();
            sb.append(" [")
                                        .append(depth)
                                        .append(" class=").append(current.getClass().getName())
                                        .append(" bg=").append(describeDrawable(background))
                    .append(" w=").append(current.getWidth())
                    .append(" h=").append(current.getHeight())
                    .append("]");
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        XposedCompat.logDebug("dolby_beta RnSettingEntryHook " + sb);
    }

    private static String describeDrawable(Drawable drawable) {
        if (drawable == null)
            return "null";
        String name = drawable.getClass().getName();
        Integer color = invokeColor(drawable, "getColor");
        Float radius = readFloatField(drawable, "mBorderRadius");
        return name + "(color=" + color +",radius=" + radius + ")";
    }

    private static Drawable copyRowBackground(View source, Context context) {
        View owner = findBestBackgroundOwner(source);
        if (owner == null)
            return null;

        Drawable drawable = owner.getBackground();
        if (drawable == null)
            return null;

        if (drawable.getConstantState() != null) {
            try {
                return drawable.getConstantState().newDrawable(context.getResources());
            } catch (Throwable ignored) {
                // RN background drawables may not carry a ConstantState.
            }
        }

        String className = drawable.getClass().getName();
        if ("com.facebook.react.views.view.ReactViewBackgroundDrawable".equals(className))
            return copyReactBackground(drawable, context);

        if (drawable instanceof ColorDrawable)
            return new ColorDrawable(((ColorDrawable) drawable).getColor());

        return drawable.mutate();
    }

    private static View findBestBackgroundOwner(View root) {
        if (root == null)
            return null;

        View best = root.getBackground() == null ? null : root;
        long bestArea = best == null ? 0L : area(best);

        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = findBestBackgroundOwner(group.getChildAt(i));
                if (child != null && child.getBackground() != null) {
                    long childArea = area(child);
                    if (childArea > bestArea) {
                        best = child;
                        bestArea = childArea;
                    }
                }
            }
        }
        return best;
    }

    private static long area(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0)
            width = Math.max(view.getMeasuredWidth(), 1);
        if (height <= 0)
            height = Math.max(view.getMeasuredHeight(), 1);
        return (long) width * height;
    }

    private static Drawable copyReactBackground(Drawable drawable, Context context) {
        GradientDrawable result = new GradientDrawable();
        Integer color = invokeColor(drawable, "getColor");
        if (color != null && Color.alpha(color) > 0)
            result.setColor(color);

        Float radius = readFloatField(drawable, "mBorderRadius");
        if (radius != null && !Float.isNaN(radius) && radius > 0f)
            result.setCornerRadius(radius);

        Integer alpha = invokeInt(drawable, "getAlpha");
        if (alpha != null && alpha < 255)
            result.setAlpha(alpha);
        return result;
    }

    private static Integer invokeColor(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            Object value = method.invoke(target);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Integer invokeInt(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            Object value = method.invoke(target);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Float readFloatField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof Float ? (Float) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void ensureArrow(ViewGroup entry, View referenceRow) {
        if (entry == null || referenceRow == null || entry.getWindowToken() == null)
            return;

        ImageView source = findArrowView(referenceRow, 0);
        if (source == null || source.getDrawable() == null)
            return;

        ImageView arrow = null;
        for (int i = 0; i < entry.getChildCount(); i++) {
            View child = entry.getChildAt(i);
            if (ARROW_TAG.equals(child.getTag())) {
                arrow = (ImageView) child;
                break;
            }
        }
        if (arrow == null) {
            arrow = new ImageView(entry.getContext());
            arrow.setTag(ARROW_TAG);
            entry.addView(arrow);
        }

        if (source.getDrawable().getConstantState() != null) {
            arrow.setImageDrawable(source.getDrawable().getConstantState()
                    .newDrawable(entry.getContext().getResources()));
        } else {
            arrow.setImageDrawable(source.getDrawable());
        }

        int width = Math.max(Math.max(source.getWidth(), source.getMeasuredWidth()), 1);
        int height = Math.max(Math.max(source.getHeight(), source.getMeasuredHeight()), 1);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        if (source.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams sourceParams =
                    (ViewGroup.MarginLayoutParams) source.getLayoutParams();
            params.leftMargin = sourceParams.leftMargin;
            params.topMargin = sourceParams.topMargin;
            params.rightMargin = sourceParams.rightMargin;
            params.bottomMargin = sourceParams.bottomMargin;
        }
        arrow.setLayoutParams(params);
    }

    private static ImageView findArrowView(View view, int depth) {
        if (view == null || depth > 12)
            return null;
        if (view instanceof ImageView
                && view.getWidth() > 0
                && view.getHeight() > 0
                && ((ImageView) view).getDrawable() != null)
            return (ImageView) view;
        if (!(view instanceof ViewGroup))
            return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            ImageView found = findArrowView(group.getChildAt(i), depth + 1);
            if (found != null)
                return found;
        }
        return null;
    }

    private static void removeEntry(Activity activity) {
        ViewGroup content = activity.findViewById(android.R.id.content);
        removeLayoutWatcher(activity, content);
        if (content == null) return;
        View entry = findEntry(content, 0);
        if (entry != null && entry.getParent() instanceof ViewGroup)
            ((ViewGroup) entry.getParent()).removeView(entry);
    }

    private static void removeLayoutWatcher(Activity activity, View observedView) {
        ViewTreeObserver.OnGlobalLayoutListener listener = LAYOUT_WATCHERS.remove(activity);
        WeakReference<View> watched = LAYOUT_WATCHER_VIEWS.remove(activity);
        // The view the listener was attached to is remembered, because the caller's content view can
        // already be null here — in that case the listener used to be dropped from the map without
        // ever being unregistered from the ViewTreeObserver.
        if (observedView == null && watched != null)
            observedView = watched.get();
        if (listener != null && observedView != null) {
            try {
                observedView.getViewTreeObserver().removeGlobalOnLayoutListener(listener);
            } catch (Throwable t) {
                XposedCompat.noteHookFailed("RnSettingEntryHook#removeLayoutWatcher", t);
            }
        }
    }

    private static View findEntry(ViewGroup parent, int depth) {
        if (parent == null || depth > 20) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (ENTRY_TAG.equals(child.getTag())) return child;
            if (child instanceof ViewGroup) {
                View found = findEntry((ViewGroup) child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextView findTextView(View view, int depth) {
        if (view == null || depth > 40) return null;
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null
                    && view.isShown()
                    && view.getWidth() > 0
                    && ACCOUNT_ROW_TEXT.contentEquals(text))
                return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findTextView(group.getChildAt(i), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static String bounds(View view) {
        return "[" + view.getLeft() + "," + view.getTop() + ", " + view.getRight() + "," + view.getBottom()
                + ", " + view.getWidth() + "x" + view.getHeight() + ", visible=" + view.isShown()
                + ", alpha=" + view.getAlpha() + "]";
    }

    private static void reportUnknownModuleOnce(String moduleName, Object container) {
        String key = moduleName == null ? "<null>" : moduleName;
        if (REPORTED_MODULE_NAMES.add(key)) {
            debug("unknown RN module once, module=" + moduleName
                    + ", container=" + (container == null ? "null" : container.getClass().getName()));
        }
    }

    private static void debug(String message) {
        XposedCompat.logDebug("RnSettingEntryHook v" + BuildConfig.VERSION_NAME + ": " + message);
    }

    private static final class ListPlacement {
        final ViewGroup row;
        final ViewGroup list;
        final int index;

        ListPlacement(ViewGroup row, ViewGroup list, int index) {
            this.row = row;
            this.list = list;
            this.index = index;
        }
    }
}
