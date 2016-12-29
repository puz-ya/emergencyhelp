package e_and_y.emergencyhelp;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.util.Log;

/**
 * Created by YD on 06.11.2016.
 */

public /* static */ class SettingsFragmentUserInfo
        //extends PreferenceFragment {
        extends AutoSummaryPreferenceFragment {

    final String[] mKeys = {"user_name", "user_comment"};

    Context mSettingsActivity = null;
    private static final String LOG_TAG = "e.y/S...entUserInfo";

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");
        addPreferencesFromResource(R.xml.settings_screen_user_info);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.d(LOG_TAG, "onActivityCreate");

        if(isAdded()){
            //Fragments are slightly unstable and getActivity returns null some times,
            //so, always check the isAdded() method of fragment before getting context by getActivity()
            mSettingsActivity = getActivity();
        }else{
            Log.d(LOG_TAG, "Couldn\'t load SettingsActivity context");
            return;
        }

        SettingsContentProvider.DatabaseHelper sql = new SettingsContentProvider.DatabaseHelper(mSettingsActivity);

        Preference myPrefName = findPreference(mKeys[0]);
        String mUserName = sql.getSettingsValue(mKeys[0]);
        myPrefName.setSummary(mUserName);
        myPrefName.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                EditTextPreference etp = (EditTextPreference) preference;
                String newHostValue = newValue.toString();

                int num = updateValuesInDB(mKeys[0], newHostValue);

                etp.setSummary(newHostValue);
                return true;
            }
        });

        Preference myPrefComment = findPreference(mKeys[1]);
        String mUserComment = sql.getSettingsValue(mKeys[1]);
        myPrefComment.setSummary(mUserComment);
        myPrefComment.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                EditTextPreference etp = (EditTextPreference) preference;
                String newHostValue = newValue.toString();

                int num = updateValuesInDB(mKeys[1], newHostValue);

                etp.setSummary(newHostValue);
                return true;
            }
        });

        //TODO: old SharedPreference + AutoSummaryPreferenceFragment
        bindKeys(mKeys);
    }

    private int updateValuesInDB(String key, String value){
        ContentValues values = new ContentValues();
        values.put(SettingsContentProvider.mSettingsValue, value);
        if(mSettingsActivity != null){
            return mSettingsActivity.getContentResolver()
                    .update(SettingsContentProvider.CONTENT_URI, values, SettingsContentProvider.mSettingsKey + " = '" + key + "'", null);
        }
        return -1;
    }

}