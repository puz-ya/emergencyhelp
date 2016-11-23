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
import android.support.annotation.NonNull;

import java.util.HashMap;

/**
 * Created by YD on 17.11.2016.
 * Replacement for old SharedPreferences (MULTI_MODE was DEPRECATED)
 *
 *
 Modifier 	Class 	Package 	Subclass 	World
 public 	    Y 	Y 	        Y 	        Y
 protected 	    Y 	Y 	        Y 	        N
 no modifier 	Y 	Y 	        N 	        N
 private 	    Y 	N 	        N 	        N
 */

@SuppressWarnings("ConstantConditions") //for getContentResolver()
public class SettingsContentProvider extends ContentProvider {

    static final String PROVIDER_NAME = "evyasonov.emergencyhelp.SettingsContentProvider";
    //for settings
    static final String URL = "content://" + PROVIDER_NAME + "/cte";
    static final Uri CONTENT_URI = Uri.parse(URL);
    //for phones list
    static final String URL_PHONES = "content://" + PROVIDER_NAME + "/cte_phones";
    static final Uri CONTENT_URI_PHONES = Uri.parse(URL_PHONES);

    //SQLLite will have 2 tables: for settings and for phone numbers (to send sms)
    protected SQLiteDatabase mDb;
    static final String DATABASE_NAME = "e.y_database";
    static final int DATABASE_VERSION = 1;

    static final String TABLE_SETTINGS = "all_settings";
    static final String TABLE_PHONES = "all_phones";
    static final String CREATE_DB_TABLE_SETTINGS = "CREATE TABLE " + TABLE_SETTINGS
            + " (name TEXT NOT NULL PRIMARY KEY, "
            + " name2 TEXT);";
    static final String INSERT_START_VALUES = "INSERT INTO " + TABLE_SETTINGS
            + " VALUES ('user_name', '...'),"
            + " ('user_comment', '...'),"
            + " ('bootload', 'false'),"
            + " ('alarm_time', '30'),"
            + " ('alarm_sensitive', '5');";    //TODO: sql

    static final String CREATE_DB_TABLE_PHONES = "CREATE TABLE " + TABLE_PHONES
            + " (id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + " number TEXT NOT NULL, "
            + " name TEXT NOT NULL);";


    static final String mSettingsKey="name";
    static final String mSettingsValue="name2";

    static final int mUriCodeSettings = 1;  //code for settins table
    static final int mUriCodePhones = 2;    //code for phones table
    static final UriMatcher mUriMatcher;
    static {
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(PROVIDER_NAME, "cte", mUriCodeSettings);
        mUriMatcher.addURI(PROVIDER_NAME, "cte/*", mUriCodeSettings);
        mUriMatcher.addURI(PROVIDER_NAME, "cte_phones", mUriCodePhones);
        mUriMatcher.addURI(PROVIDER_NAME, "cte_phones/*", mUriCodePhones);
    }
    private static HashMap<String,String> mValues;  //uses in query()

    @Override
    public boolean onCreate(){
        Context context = getContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        mDb = dbHelper.getWritableDatabase();

        return (mDb != null);
    }


    @Override
    public String getType(@NonNull Uri uri){
        switch (mUriMatcher.match(uri)){
            case mUriCodeSettings:
                return "vnd.android.cursor.dir/cte";

            case mUriCodePhones:
                return "vnd.android.cursor.dir/cte_phones";

            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs){
        int count = 0;
        switch (mUriMatcher.match(uri)){
            case mUriCodeSettings:
                count = mDb.delete(TABLE_SETTINGS, selection, selectionArgs);
                break;
            case mUriCodePhones:
                count = mDb.delete(TABLE_PHONES, selection, selectionArgs);
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
    public Uri insert(@NonNull Uri uri, ContentValues values){

        long rowId = 0;
        Uri tmpUri = CONTENT_URI;   //default

        switch (mUriMatcher.match(uri)){
            case mUriCodeSettings:
                rowId = mDb.insert(TABLE_SETTINGS, "", values);
                tmpUri = CONTENT_URI;
                break;
            case mUriCodePhones:
                rowId = mDb.insert(TABLE_PHONES, "", values);
                tmpUri = CONTENT_URI_PHONES;
                break;
        }

        if(rowId > 0){
            Uri _uri = ContentUris.withAppendedId(tmpUri, rowId);
            if(getContext().getContentResolver() != null) {
                getContext().getContentResolver().notifyChange(_uri, null);
            }else{
                throw new IllegalArgumentException("Unknown URI: " + uri);
            }
            return _uri;
        }
        throw new SQLException("Failed to add record: " + uri);
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder){
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        switch (mUriMatcher.match(uri)){
            case mUriCodeSettings:
                qb.setTables(TABLE_SETTINGS);
                qb.setProjectionMap(mValues);
                if(sortOrder == null || sortOrder.equals("")){
                    sortOrder = "name";
                }
                break;

            case mUriCodePhones:
                qb.setTables(TABLE_PHONES);
                qb.setProjectionMap(mValues);
                if(sortOrder == null || sortOrder.equals("")){
                    sortOrder = "name"; //name of the phone number
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        Cursor cur = qb.query(mDb, projection, selection, selectionArgs, null, null, sortOrder);
        cur.setNotificationUri(getContext().getContentResolver(), uri);
        return cur;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs){
        int count = 0;
        switch (mUriMatcher.match(uri)){
            case mUriCodeSettings:
                count = mDb.update(TABLE_SETTINGS, values, selection, selectionArgs);
                break;

            case mUriCodePhones:
                count = mDb.update(TABLE_PHONES, values, selection, selectionArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    //NEW DATABASE CLASS
    public static class DatabaseHelper extends SQLiteOpenHelper{
        DatabaseHelper(Context context){
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db){
            db.execSQL(CREATE_DB_TABLE_SETTINGS);
            db.execSQL(INSERT_START_VALUES);
            db.execSQL(CREATE_DB_TABLE_PHONES);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion){
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_SETTINGS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_PHONES);
            onCreate(db);
        }

        String getSettingsValue(String sKey){

            SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = null;
            String empName = "";

            cursor = db.rawQuery("SELECT "+mSettingsValue+" FROM "
                    + TABLE_SETTINGS
                    + " WHERE  "+mSettingsKey+" =?", new String[] {sKey});

            if(cursor.getCount() > 0) {
                cursor.moveToFirst();
                empName = cursor.getString(cursor.getColumnIndex(mSettingsValue));
            }
            cursor.close();

            return empName;
        }
    }
}
