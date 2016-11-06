package evyasonov.emergencyhelp;

import android.os.Bundle;

/**
 * Created by YD on 06.11.2016.
 */

public /* static */ class SettingsFragmentUserInfo extends AutoSummaryPreferenceFragment {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_screen_user_info);

        final String[] keys = {"user_name", "user_comment"};
        bindKeys(keys);
    }
}