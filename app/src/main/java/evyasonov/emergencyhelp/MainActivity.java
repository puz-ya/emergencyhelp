package evyasonov.emergencyhelp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.util.Arrays;
import java.util.TreeSet;


public class MainActivity extends Activity implements CompoundButton.OnCheckedChangeListener {

    public static final String SETTINGS_TAB = "evyasonov.emergencyhelp.SETTINGS_TAB";

    private static final String LOG_TAG = "evyasonov/MainActivity";


    private Switch mStatusSwitch;
    private SharedPreferences mSharedPreferences;

    private int mLastDialogNumber = 1;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");

        mSharedPreferences = getApplicationContext()
                .getSharedPreferences(SettingsActivity.PREFERENCES_FILENAME, Context.MODE_MULTI_PROCESS);

        executeDialogList();

        setContentView(R.layout.activity_main);

        mStatusSwitch = (Switch) findViewById(R.id.statusSwitch);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume");

        mStatusSwitch.setOnCheckedChangeListener(null);
        mStatusSwitch.setChecked(AccelerometerMonitoringService.isServiceRunning(this));
        mStatusSwitch.setOnCheckedChangeListener(this);
    }

    @Override
    public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
        Log.d(LOG_TAG, "onCheckedChanged");

        if (isChecked) {
            alarmOn();
        } else {
            alarmOff();
        }
    }


    private void alarmOn() {
        Log.d(LOG_TAG, "alarmOn");

        if (mSharedPreferences.getStringSet("emergency_contacts", new TreeSet<String>()).isEmpty()) {

            final Activity thisActivity = this;

            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.no_numbers_in_settings))
                    .setPositiveButton(
                            getString(R.string.no_numbers_in_settings_positive_button),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent intent = new Intent(thisActivity, SettingsActivity.class);
                                    intent.putExtra(SETTINGS_TAB, "phones");
                                    startActivity(intent);

                                    dialogInterface.dismiss();
                                }
                            }
                    )
                    .setNegativeButton(
                            getString(R.string.no_numbers_in_settings_negative_button),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                }
                            }
                    )
                    .create()
                    .show();

            mStatusSwitch.setChecked(false);
        } else {
            startService(new Intent("evyasonov.emergencyhelp.AccelerometerMonitoringService"));

            final LocationManager mLocationManager =
                    (LocationManager) getSystemService(LOCATION_SERVICE);

            if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.gps_off_dialog_description))
                        .setPositiveButton(
                                getString(R.string.gps_off_dialog_positive_button),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                                        dialogInterface.dismiss();
                                    }
                                }
                        )
                        .setNegativeButton(
                                getString(R.string.gps_off_dialog_negative_button),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.cancel();
                                    }
                                }
                        )
                        .create()
                        .show();
            }
        }
    }

    private void alarmOff() {
        Log.d(LOG_TAG, "alarmOff");

        stopService(new Intent("evyasonov.emergencyhelp.AccelerometerMonitoringService"));
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();

        switch (id) {
            case R.id.action_menu_deep_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case R.id.action_menu_view_sms:
                final LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

                final String message = AccelerometerMonitoringServiceSmsGenerator.generate(
                        getApplicationContext(),
                        mSharedPreferences.getString("user_name", ""),
                        mSharedPreferences.getString("user_comment", ""),
                        locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));

                showOneButtonDialog(
                        getString(R.string.sms_pretext) + "\n" + message,
                        getString(R.string.sms_positive_button)
                );
                break;

            case R.id.action_menu_licence:
                showOneButtonDialog(
                        getString(R.string.licence_text),
                        getString(R.string.license_positive_button)
                );
                break;

            case R.id.action_menu_about:
                showOneButtonDialog(
                        getString(R.string.about_text),
                        getString(R.string.about_positive_button)
                );
                break;

            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }


    private void initPreferences() {
        mSharedPreferences
                .edit()
                .putString("licence_accepted", "yes")
                .putStringSet(
                        "emergency_contacts",
                        new TreeSet<String>(
                                Arrays.asList(getString(R.string.emergency_number_default))))
                .apply();
    }

    //changed to protected to be able to run everywhere here
    public void showOneButtonDialog(final String text, final String positiveButtonText) {
        showOneButtonDialog(this, text, positiveButtonText);
    }


    public static void showOneButtonDialog(final Activity activity, final String message, final String positiveButtonText) {
        new AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(
                        positiveButtonText,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                dialogInterface.dismiss();
                            }
                        }
                )
                .create()
                .show();
    }

    public static void exitDialog(final Activity activity, final String message, final String exitButtonText) {
        new AlertDialog.Builder(activity)
                .setMessage(message)
                .setPositiveButton(
                        exitButtonText,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                activity.finish();
                                dialogInterface.dismiss();
                            }
                        }
                )
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        activity.finish();
                    }
                })
                .create()
                .show();
    }

    public void executeDialogList() {
        if (mLastDialogNumber == 1) {
            final SensorManager sensorManager =
                    (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
            final Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometerSensor == null) {
                exitDialog(
                        this,
                        getString(R.string.device_has_no_accelerometer),
                        getString(R.string.device_has_no_accelerometer_exit));
            } else {
                mLastDialogNumber = 2;
            }
        }

        if (mLastDialogNumber == 2) {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            //TODO: не дописано
            if (false && telephonyManager.getSimState() != TelephonyManager.SIM_STATE_READY) {
                exitDialog(
                        this,
                        getString(R.string.sim_is_not_available_description),
                        getString(R.string.sim_is_not_available_positive_button));
            } else {
                mLastDialogNumber = 3;
            }
        }

        if (mLastDialogNumber == 3) {
            if ( ! mSharedPreferences.getString("licence_accepted", "").equals("yes")) {
                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.licence_text))
                        .setPositiveButton(
                                getString(R.string.licence_accept_button),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        initPreferences();
                                        dialogInterface.dismiss();

                                        mLastDialogNumber = 4;
                                        executeDialogList();
                                    }
                                }
                        )
                        .setNegativeButton(
                                getString(R.string.licence_decline_button),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        finish();
                                        dialogInterface.dismiss();
                                    }
                                }
                        )
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialogInterface) {
                                finish();
                            }
                        })
                        .create()
                        .show();
            } else {
                mLastDialogNumber = 4;
            }
        }

        if (mLastDialogNumber == 4) {
            if ( ! mSharedPreferences.getString("first_launch", "").equals("no")) {
                mSharedPreferences.edit().putString("first_launch", "no").apply();

                new AlertDialog.Builder(this)
                        .setMessage(getString(R.string.first_launch_open_settings_dialog_description))
                        .setPositiveButton(
                                getString(R.string.first_launch_open_settings_positive_button),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        startActivity(new Intent(getApplicationContext(), SettingsActivity.class));
                                        dialogInterface.dismiss();
                                    }
                                }
                        )
                        .setNegativeButton(
                                getString(R.string.first_launch_open_settings_negative_button),
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {
                                        dialogInterface.dismiss();
                                    }
                                }
                        )
                        .create()
                        .show();
            }
        }
    }
}