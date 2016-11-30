package e_and_y.emergencyhelp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;

//On boot, if set to TRUE in settings

public class BootBroadcastReceiver extends BroadcastReceiver {
    public BootBroadcastReceiver() {
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("bootload", false)) {
            context.startService(new Intent(context, AccelerometerMonitoringService.class));
        }
    }
}
