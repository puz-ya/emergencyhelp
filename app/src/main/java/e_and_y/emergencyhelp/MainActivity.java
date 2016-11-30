package e_and_y.emergencyhelp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.Layout;
import android.text.SpannableString;
import android.text.style.AlignmentSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.util.Arrays;
import java.util.TreeSet;

import static java.lang.String.valueOf;


public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    public static final String SETTINGS_TAB = "e_and_y.emergencyhelp.SETTINGS_TAB";

    private static final String LOG_TAG = "e.y/MainActivity";


    private Switch mStatusSwitch;
    private SharedPreferences mSharedPreferences;

    private int mLastDialogNumber = 1;
    private static final int INITIAL_REQUEST_4LOCATION = 1337;

    private Location mLocation = null;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");

        mSharedPreferences = getApplicationContext()
                .getSharedPreferences(SettingsActivity.PREFERENCES_FILENAME, Context.MODE_MULTI_PROCESS);

        executeDialogList();

        setContentView(R.layout.activity_main);

        if(getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setIcon(R.mipmap.ic_launcher);
        }

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
    public void onDestroy(){
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");
    }

    //Checking switch button of service off/on
    @Override
    public void onCheckedChanged(final CompoundButton compoundButton, final boolean isChecked) {
        Log.d(LOG_TAG, "onCheckedChanged");

        if (isChecked) {
            setAccelServiceOn();
        } else {
            setAccelServiceOff();
        }
    }


    private void setAccelServiceOn() {
        Log.d(LOG_TAG, "setAccelServiceOn");

        if (mSharedPreferences.getStringSet("emergency_contacts", new TreeSet<String>()).isEmpty()) {

            Log.d(LOG_TAG, "emergencyContactsAreEmpty");

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

            Log.d(LOG_TAG, "emergencyContactsNOTEmpty");

            Intent intent = new Intent(this, AccelerometerMonitoringService.class);
            ComponentName componentName = startService(intent); //componentName just for debug

            Log.d(LOG_TAG, "startService");


            //checking GPS permissions (API 21+) FINE_LOCATION is enough for both NETWORK & GPS
            if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Consider calling ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission.
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                        INITIAL_REQUEST_4LOCATION
                );

                Toast.makeText(this, "Please, enable LOCATION ACCESS in settings.", Toast.LENGTH_SHORT).show();
            }else{


                MyLocation.LocationResult locationResult = new MyLocation.LocationResult(){
                    @Override
                    public void gotLocation(Location location){
                        //Got the location!
                        mLocation = location;
                    }
                };
                MyLocation myLocation = new MyLocation();
                boolean bRes = myLocation.getLocation(this, locationResult);

                if(mLocation != null){
                    Toast.makeText(MainActivity.this, "_SUCCESS!_", Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(MainActivity.this, "_FAILED!_", Toast.LENGTH_LONG).show();
                }

                checkLocationManagerAndShowDialog();
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case INITIAL_REQUEST_4LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the task you need to do.
                    Toast.makeText(this, "Permission was granted, thank you!.", Toast.LENGTH_SHORT).show();
                    checkLocationManagerAndShowDialog();

                } else {

                    // permission denied! Disable the functionality that depends on this.
                    Toast.makeText(this, "Permission was not granted, aborting...", Toast.LENGTH_SHORT).show();
                    finish();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void onClickStartAlarm(View view){
        final Intent intent = new Intent(this, AlarmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void checkLocationManagerAndShowDialog(){

        final LocationManager locationManager =
                (LocationManager) this.getSystemService(LOCATION_SERVICE);

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
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

    private void setAccelServiceOff() {
        Log.d(LOG_TAG, "setAccelServiceOff");

        stopService(new Intent("e_and_y.emergencyhelp.AccelerometerMonitoringService"));
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        //text of settings now right in the center
        int nMenuItemsCount = menu.size();
        for (int i=0; i<nMenuItemsCount; i++){
            MenuItem item = menu.getItem(i);
            SpannableString sOptionsString = new SpannableString(item.getTitle());

            sOptionsString.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), 0, sOptionsString.length(), 0);

            item.setTitle(sOptionsString);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int id = item.getItemId();

        switch (id) {
            case R.id.action_menu_deep_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(intent);
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

            case R.id.action_menu_close:
                setAccelServiceOff();
                finish();
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

    //
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

    public void onClickAddName(View view) {
        ContentValues values = new ContentValues();
        String sName1 = "YD key";//((EditText) findViewById(R.id.txtName)).getText().toString();
        String sName2 = "YD value";

        values.put(SettingsContentProvider.mSettingsKey, sName1);
        values.put(SettingsContentProvider.mSettingsValue, sName2);
        Uri uri = getContentResolver().insert(SettingsContentProvider.CONTENT_URI, values);
        //Uri uri2 = getContentResolver().update(...);

        Toast.makeText(getBaseContext(), "New record inserted", Toast.LENGTH_SHORT)
                .show();
    }
}