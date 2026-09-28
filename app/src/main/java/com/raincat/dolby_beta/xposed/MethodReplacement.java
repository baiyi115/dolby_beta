package com.raincat.dolby_beta.xposed;

public abstract class MethodReplacement extends MethodHook {

    public abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        param.setResult(replaceHookedMethod(param));
    }

    public static MethodReplacement returnConstant(final Object value) {
        return new MethodReplacement() {
            @Override
            public Object replaceHookedMethod(MethodHookParam param) {
                return value;
            }
        };
    }
}
