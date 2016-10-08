package com.pescod.multiplethreadcontinuabledownloader.DB;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by Pescod on 2/1/2016.
 */
public class DBOpenHelper extends SQLiteOpenHelper {
    private static final String DBNAME = "eric.db";
    private static final int VERSION = 1;

    public DBOpenHelper(Context context) {
        super(context, DBNAME, null, VERSION);
    }

    /**
     * 建立数据库
     *
     * @param db
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS filedownlog(" +
                "Id integer primary key autoincrement," +
                "downPath varchar(100)," +
                "threadId INTEGER," +
                "downLength INTEGER");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS filedownlog");
        onCreate(db);
    }
}