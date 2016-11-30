package e_and_y.emergencyhelp;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TabHost;
import android.widget.TextView;


public class SettingsActivity
        extends FragmentActivity
        implements AutoSummaryPreferenceFragment.OnSettingsChanged, LoaderManager.LoaderCallbacks<Cursor>
{
    public static final String PREFERENCES_FILENAME = "preferences";
    private static final String LOG_TAG = "e.y/SettingsActivity";
    private static final int PIXELS_IN_MOTION = 70;

    /* reading values from content provider */
    TextView mResultView=null;
    CursorLoader mCursorLoader;
    /* ----- */

    private MessengerToAccelerometerMonitoringService mService;
    private TabHost mTabHost;
    private float mLastX;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");

        setContentView(R.layout.activity_settings);

        //for ContentProvider only
        //mResultView = (TextView) findViewById(R.id.res);

        final String activeTabName = getIntent().getStringExtra(MainActivity.SETTINGS_TAB);

        mTabHost = (TabHost) findViewById(R.id.tabHost);
        mTabHost.setup();

        mTabHost.addTab(
                mTabHost
                        .newTabSpec("info")
                        .setIndicator(getString(R.string.settings_tab_1_text))
                        .setContent(R.id.tab1)
        );

        mTabHost.addTab(
                mTabHost
                        .newTabSpec("phones")
                        .setIndicator(getString(R.string.settings_tab_2_text))
                        .setContent(R.id.tab2)
        );

        mTabHost.addTab(
                mTabHost
                        .newTabSpec("other")
                        .setIndicator(getString(R.string.settings_tab_3_text))
                        .setContent(R.id.tab3)
        );

        //переставлено правильно 2016.11.06
        mTabHost.setCurrentTabByTag(activeTabName == null ? "info" : activeTabName);

        mService = new MessengerToAccelerometerMonitoringService(getApplicationContext(), null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume");

        mService.bindService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        mService.unbindService();
    }


    @Override
    public void onSettingsChanged() {
        Log.d(LOG_TAG, "onSettingsChanged");

        mService.sendMessageToService(AccelerometerMonitoringService.MSG_PREFERENCES_WAS_CHANGED);
    }


    //Перемещаемся между табами пальцами (прокрутка)
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(LOG_TAG, "onTouchEvent");

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastX = event.getX();
                break;

            case MotionEvent.ACTION_UP:
                float currentX = event.getX();

                // if left to right swipe on screen
                if (mLastX < currentX - PIXELS_IN_MOTION) {
                    mTabHost.setCurrentTab(mTabHost.getCurrentTab() - 1);
                }

                // if right to left swipe on screen
                if (mLastX > currentX + PIXELS_IN_MOTION) {
                    mTabHost.setCurrentTab(mTabHost.getCurrentTab() + 1);
                }

                break;
        }

        return false;
    }

    //Because we decided not to let users kill an app from Android App List
    public void onClickFinishSettingsActivity(View view){
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        startActivity(intent);
    }

    /* content provider part */
    public void onClickDisplayNames(View view){
        getSupportLoaderManager().initLoader(1, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1){
        mCursorLoader = new CursorLoader(this, Uri.parse("content://e_and_y.emergencyhelp.SettingsContentProvider/cte"), null, null, null, null);
        return mCursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor){
        cursor.moveToFirst();
        StringBuilder res = new StringBuilder();

        while(!cursor.isAfterLast()){
            res.append("\n"+cursor.getString(cursor.getColumnIndex("id"))
                    + " - "
                    + cursor.getString(cursor.getColumnIndex("name"))
                    + " - "
                    + cursor.getString(cursor.getColumnIndex("name2"))
            );
            cursor.moveToNext();
        }

        mResultView.setText(res);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0){
        //auto-generated
    }

}
