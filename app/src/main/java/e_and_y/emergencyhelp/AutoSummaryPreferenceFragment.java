package e_and_y.emergencyhelp;

import android.app.Activity;
import android.content.Context;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.RingtonePreference;
import android.text.TextUtils;


public class AutoSummaryPreferenceFragment extends PreferenceFragment {

    public interface OnSettingsChanged {
        public void onSettingsChanged();
    }


    protected boolean mIsChangesByUser = false;
    private OnSettingsChanged mActivityNotifier;


    public AutoSummaryPreferenceFragment() {
        super();
    }


    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);

        try {
            mActivityNotifier = (OnSettingsChanged) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnSettingsChanged");
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getPreferenceManager().setSharedPreferencesName(SettingsActivity.PREFERENCES_FILENAME);
        getPreferenceManager().setSharedPreferencesMode(Context.MODE_MULTI_PROCESS);
    }


    protected void bindKeys(final String[] keys) {
        mIsChangesByUser = false;
        for (final String key : keys) {
            bindPreferenceSummaryToValue(findPreference(key));
        }
        mIsChangesByUser = true;
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    protected final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object value) {
                    final String stringValue = value.toString();

                    if (preference instanceof ListPreference) {
                        // For list preferences, look up the correct display value in
                        // the preference's 'entries' list.
                        final ListPreference listPreference = (ListPreference) preference;
                        final int index = listPreference.findIndexOfValue(stringValue);

                        // Set the summary to reflect the new value.
                        preference.setSummary(
                                index >= 0
                                        ? listPreference.getEntries()[index]
                                        : null);

                    } else if (preference instanceof RingtonePreference) {
                        // For ringtone preferences, look up the correct display value
                        // using RingtoneManager.
                        if (TextUtils.isEmpty(stringValue)) {
                            // Empty values correspond to 'silent' (no ringtone).
                            preference.setSummary("Silent");

                        } else {
                            final Ringtone ringtone = RingtoneManager.getRingtone(
                                    preference.getContext(), Uri.parse(stringValue));

                            if (ringtone == null) {
                                // Clear the summary if there was a lookup error.
                                preference.setSummary(null);
                            } else {
                                // Set the summary to reflect the new ringtone display
                                // name.
                                final String name = ringtone.getTitle(preference.getContext());
                                preference.setSummary(name);
                            }
                        }

                    } else {
                        // For all other preferences, set the summary to the value's
                        // simple string representation.
                        preference.setSummary(stringValue);
                    }

                    if (mIsChangesByUser) {
                        mActivityNotifier.onSettingsChanged();
                    }

                    return true;
                }
            };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    protected void bindPreferenceSummaryToValue(final Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(
                preference,
                getPreferenceManager().getSharedPreferences().getString(preference.getKey(), ""));
    }
}
