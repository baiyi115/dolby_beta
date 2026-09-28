package com.raincat.dolby_beta.xposed;

import java.lang.reflect.Executable;

/**
 * Hook callback base. Semantics follow the classic MethodHook, implemented on
 * libxposed API 102 Chain/intercept: not calling proceed() in [before] returns
 * early, [after] may replace the result.
 */
public abstract class MethodHook {

    /** Shared empty arg array for hooks that declared {@link #usesArgs()} false. */
    private static final Object[] NO_ARGS = new Object[0];

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
    }

    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
    }

    /**
     * Return false when this hook never reads or writes {@code param.args}. The dispatcher then
     * skips copying the argument list into a fresh array on every call, which matters for hooks
     * that fire once per HTTP request or once per trial-state getter.
     */
    protected boolean usesArgs() {
        return true;
    }

    public static class MethodHookParam {
        public final Executable method;
        public final Object thisObject;
        public final Object[] args;

        /** Set before the original call to skip it, replaced after it. */
        public boolean returnEarly;
        public Object result;

        MethodHookParam(Executable method, Object thisObject, Object[] args) {
            this.method = method;
            this.thisObject = thisObject;
            this.args = args;
        }

        public void setResult(Object result) {
            this.result = result;
            this.returnEarly = true;
        }

        public Object getResult() {
            return result;
        }
    }

    /** Bridge into the libxposed Chain, called by XposedCompat.hookMethod. */
    static Object dispatch(MethodHook hook, Executable exec, io.github.libxposed.api.XposedInterface.Chain chain) throws Throwable {
        boolean usesArgs = hook.usesArgs();
        Object[] args = usesArgs ? argsOf(chain) : NO_ARGS;
        MethodHookParam param = new MethodHookParam(exec, chain.getThisObject(), args);
        try {
            hook.beforeHookedMethod(param);
        } catch (Throwable t) {
            XposedCompat.log("hook callback threw [before] on " + exec + " hook=" + hook.getClass().getName());
            XposedCompat.log(t);
            return proceed(chain, usesArgs, param.args);
        }
        if (param.returnEarly)
            return param.result;
        param.result = proceed(chain, usesArgs, param.args);
        try {
            hook.afterHookedMethod(param);
        } catch (Throwable t) {
            XposedCompat.log("hook callback threw [after] on " + exec + " hook=" + hook.getClass().getName());
            XposedCompat.log(t);
        }
        return param.result;
    }

    private static Object[] argsOf(io.github.libxposed.api.XposedInterface.Chain chain) {
        java.util.List<Object> chainArgs = chain.getArgs();
        return chainArgs == null ? NO_ARGS : chainArgs.toArray();
    }

    /** The no-arg proceed() reuses the original arguments, avoiding a copy for arg-free hooks. */
    private static Object proceed(io.github.libxposed.api.XposedInterface.Chain chain,
                                  boolean usesArgs, Object[] args) throws Throwable {
        return usesArgs ? chain.proceed(args) : chain.proceed();
    }
}
