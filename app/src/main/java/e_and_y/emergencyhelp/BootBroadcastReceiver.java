package e_and_y.emergencyhelp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

//On boot, if set to TRUE in settings

public class BootBroadcastReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "e.y/BootReceiver";
    private SharedPreferences mSharedPreferences;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        //boolean bOnBoot = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("bootload", false);

        mSharedPreferences = context.getSharedPreferences(SettingsActivity.PREFERENCES_FILENAME, Context.MODE_PRIVATE);
        boolean bOnBoot = mSharedPreferences.getBoolean("bootload",false);

        if (bOnBoot) {
            context.startService(new Intent(context, AccelerometerMonitoringService.class));
        }
        Log.i(LOG_TAG, "Start after reboot_" + bOnBoot);
    }
}
