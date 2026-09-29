package com.raincat.dolby_beta.helper;

import android.content.Context;

import com.raincat.dolby_beta.db.ExtraDao;

public class ExtraHelper {

    public static final String SCRIPT_STATUS = "script_status";

    public static final String APP_VERSION = "app_version";

    public static final String SCRIPT_ASSET_VERSION = "script_asset_version";

    public static final String USER_ID = "user_id";
    public static final String COOKIE = "cookie";

    public static final String LOVE_PLAY_LIST = "play_list";

    public static void init(Context context) {
        ExtraDao.init(context);
    }

    /**
     * Extra values are read from SQLite on nearly every hooked trial-state getter
     * (via TrialStateHook.shouldSuppressTrial), and ExtraDao opens/closes the database
     * around each query, so a raw read per hook call is far too expensive.
     * A short TTL keeps cross-process writes (main <-> :play) visible within ~1s.
     */
    private static final long CACHE_TTL_MS = 1000L;

    private static final java.util.concurrent.ConcurrentHashMap<String, CachedExtra> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final class CachedExtra {
        final String value;
        final long expiresAt;

        CachedExtra(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }
    }

    public static String getExtraDate(String key) {
        long now = System.currentTimeMillis();
        CachedExtra cached = CACHE.get(key);
        if (cached != null && cached.expiresAt > now && cached.value != null)
            return cached.value;
        String value = ExtraDao.getInstance().getExtra(key);
        // Callers compare the result with "-1" (or call .equals on it), so a NULL column must never
        // escape here: it would turn every "not fetched yet" check into a NullPointerException.
        if (value == null)
            value = "-1";
        CACHE.put(key, new CachedExtra(value, now + CACHE_TTL_MS));
        return value;
    }

    public static void setExtraDate(String key, Object value) {
        String text = value == null ? "-1" : value.toString();
        CACHE.put(key, new CachedExtra(text, System.currentTimeMillis() + CACHE_TTL_MS));
        ExtraDao.getInstance().saveExtra(key, text);
    }

    public static void cleanUserData() {
        setExtraDate(COOKIE, "-1");
        setExtraDate(USER_ID, "-1");
        setExtraDate(LOVE_PLAY_LIST, "-1");
    }
}
