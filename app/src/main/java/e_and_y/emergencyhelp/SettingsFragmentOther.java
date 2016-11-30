package e_and_y.emergencyhelp;

import android.os.Bundle;

/**
 * Created by YD on 06.11.2016.
 */

public /* static */ class SettingsFragmentOther extends AutoSummaryPreferenceFragment {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings_screen_other);

        final String[] keys = {"alarm_time", "alarm_sensitive"};
        bindKeys(keys);
    }
}