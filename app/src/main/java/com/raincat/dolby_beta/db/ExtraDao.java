package com.raincat.dolby_beta.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class ExtraDao {
    static final String TABLE_NAME = "extra";
    static final String EXTRA_KEY = "extra_key";
    static final String EXTRA_VALUE = "extra_value";

    private ExtraDbOpenHelper dbHelper;
    static private ExtraDao dao;

    private ExtraDao(Context context) {
        dbHelper = ExtraDbOpenHelper.getInstance(context);
    }

    public static synchronized ExtraDao getInstance() {
        return dao;
    }

    public static void init(Context context) {
        dao = new ExtraDao(context);
    }

    public synchronized void saveExtra(String key, String value) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        if (db.isOpen()) {
            ContentValues values = new ContentValues();
            values.put(EXTRA_KEY, key);
            values.put(EXTRA_VALUE, value);
            db.replace(TABLE_NAME, null, values);
        }
        db.close();
    }

    public synchronized String getExtra(String key) {
        String extra = "-1";
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        if (db.isOpen()) {
            Cursor cursor = null;
            try {
                cursor = db.rawQuery("select * from " + TABLE_NAME + " where " + EXTRA_KEY + " = '" + key + "'", null);
                if (cursor.moveToNext()) {
                    String value = cursor.getString(cursor.getColumnIndex(EXTRA_VALUE));
                    if (value != null)
                        extra = value;
                }
            } finally {
                // The cursor used to leak whenever getString()/getColumnIndex() threw.
                if (cursor != null)
                    cursor.close();
                db.close();
            }
        } else {
            db.close();
        }
        return extra;
    }
}
