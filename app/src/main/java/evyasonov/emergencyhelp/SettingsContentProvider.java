package evyasonov.emergencyhelp;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.net.Uri;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;

import java.util.HashMap;

/**
 * Created by YD on 17.11.2016.
 */

public class SettingsContentProvider extends ContentProvider {

    static final String PROVIDER_NAME = "evyasonov.emergencyhelp.SettingsContentProvider";
    static final String URL = "content://" + PROVIDER_NAME + "/cte";
    static final Uri CONTENT_URI = Uri.parse(URL);

    //SQLLite will have a table called 'names'. The table names have two columns id,name.
    private SQLiteDatabase mDb;
    static final String DATABASE_NAME = "e.y_database";
    static final String TABLE_NAME = "all_settings";
    static final int DATABASE_VERSION = 1;
    static final String CREATE_DB_TABLE = "CREATE TABLE " + TABLE_NAME
            + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + " name TEXT NOT NULL, "
            + " name2 TEXT NOT NULL);";

    static final String mId = "id";
    static final String mName1="name";
    static final String mName2="name2";

    static final int mUriCode = 1;
    static final UriMatcher mUriMatcher;

    private static HashMap<String,String> mValues;
    static {
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(PROVIDER_NAME, "cte", mUriCode);
        mUriMatcher.addURI(PROVIDER_NAME, "cte/*", mUriCode);
    }

    @Override
    public boolean onCreate(){
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        mDb = dbHelper.getWritableDatabase();
        if(mDb != null){
            return true;
        }
        return false;
    }


    @Override
    public String getType(Uri uri){
        switch (mUriMatcher.match(uri)){
            case mUriCode:
                return "vnd.android.cursor.dir/cte";

            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs){
        int count = 0;
        switch (mUriMatcher.match(uri)){
            case mUriCode:
                count = mDb.delete(TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Uknown URI" + uri);
        }

        if(getContext().getContentResolver() != null) {
            getContext().getContentResolver().notifyChange(uri, null);
        }else{
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        return count;
    }


    @Override
    public Uri insert(Uri uri, ContentValues values){
        long rowId = mDb.insert(TABLE_NAME, "", values);
        if(rowId > 0){
            Uri _uri = ContentUris.withAppendedId(CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(_uri, null);
            return _uri;
        }
        throw new SQLException("Failed to add record: " + uri);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder){
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TABLE_NAME);

        switch (mUriMatcher.match(uri)){
            case mUriCode:
                qb.setProjectionMap(mValues);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        if(sortOrder == null || sortOrder.equals("")){
            sortOrder = mName1;
        }

        Cursor cur = qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);
        cur.setNotificationUri(getContext().getContentResolver(), uri);
        return cur;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs){
        int count = 0;
        switch (mUriMatcher.match(uri)){
            case mUriCode:
                count = mDb.update(TABLE_NAME, values, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    //NEW DATABASE CLASS
    private static class DatabaseHelper extends SQLiteOpenHelper{
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db){
            db.execSQL(CREATE_DB_TABLE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            onCreate(db);
        }
    }
}
