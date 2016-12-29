package e_and_y.emergencyhelp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.text.Layout;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.AlignmentSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.TreeSet;


public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    public static final String SETTINGS_TAB = "e_and_y.emergencyhelp.SETTINGS_TAB";
    private static final String LOG_TAG = "e.y/MainActivity";
    private static final int INITIAL_REQUEST_4LOCATION = 1337;

    private Switch mStatusSwitch;
    private SharedPreferences mSharedPreferences;
    private int mLastDialogNumber = 1;
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

    @Override
    public void onPause(){
        super.onPause();
        Log.d(LOG_TAG, "onPause");
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


    private void setAccelServiceOff() {
        Log.d(LOG_TAG, "setAccelServiceOff");

        Intent accelService = new Intent("e_and_y.emergencyhelp.AccelerometerMonitoringService");
        accelService.setPackage("e_and_y.emergencyhelp");   //need to set package from security risk
        stopService(accelService);
    }

    private void setAccelServiceOn() {
        Log.d(LOG_TAG, "setAccelServiceOn");

        //Check user name
        if(mSharedPreferences.getString("user_name", "").isEmpty()){
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

                                    // SWITCH OFF
                                    setAccelServiceOff();
                                    dialogInterface.dismiss();
                                }
                            }
                    )
                    .create()
                    .show();

            mStatusSwitch.setChecked(false);
            return;
        }

        //Check contact list
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

            //checking GPS permissions (API 21+) FINE_LOCATION is enough for both NETWORK & GPS
            if (ActivityCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Consider calling ActivityCompat#requestPermissions and then overriding
                // public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults)
                // to handle the case where the user grants the permission.
                ActivityCompat.requestPermissions(
                        MainActivity.this,
                        new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
                        INITIAL_REQUEST_4LOCATION
                );

                Toast.makeText(this, getString(R.string.location_enable_location_access), Toast.LENGTH_SHORT).show();
            }else{

                // if PROVIDER is DISABLED, we don't start service, we show warning
                if(showDialogCheckLocationManager()){
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
                        Toast.makeText(MainActivity.this, getString(R.string.location_was_found), Toast.LENGTH_LONG).show();
                    }else{
                        Toast.makeText(MainActivity.this, getString(R.string.location_was_not_found), Toast.LENGTH_LONG).show();
                    }

                    //After all checks we can start service
                    Log.d(LOG_TAG, "startService");
                    Intent intent = new Intent(this, AccelerometerMonitoringService.class);
                    ComponentName componentName = startService(intent); //componentName just for debug
                }
            }

        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case INITIAL_REQUEST_4LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the task you need to do.
                    Toast.makeText(this, getString(R.string.location_permission_granted), Toast.LENGTH_SHORT).show();
                    showDialogCheckLocationManager();

                } else {

                    // permission denied! Disable the functionality that depends on this.
                    Toast.makeText(this, getString(R.string.location_permission_not_granted), Toast.LENGTH_SHORT).show();
                    finish();
                }
                //return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void onClickStartAlarm(View view){
        Log.d(LOG_TAG, "onClickStartAlarm");

        final Intent intent = new Intent(this, AlarmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("IsTestClick", true);   //for some text changes
        startActivity(intent);
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

    public void showSMSText(View view){
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
                showSMSText(null);  //no View needed
                break;

            case R.id.action_menu_agreement:
                showOneButtonDialog(
                        getString(R.string.licence_text),
                        getString(R.string.licence_accept_button)
                );
                break;

            case R.id.action_menu_about:
                showOneButtonDialog(
                    getString(R.string.about_text),
                    getString(R.string.about_positive_button)
                );
                break;

            case R.id.action_menu_authors:
                showOneButtonDialogAuthors(this);
                break;

            case R.id.action_menu_close:
                //don't stop service here, because user can switch it off manually in MainActivity
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
                .putBoolean("bootload", true)
                .apply();
    }

    //
    public void executeDialogList() {
        if (mLastDialogNumber == 1) {
            final SensorManager sensorManager =
                    (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
            final Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometerSensor == null) {
                showExitDialog(
                        this,
                        getString(R.string.device_has_no_accelerometer),
                        getString(R.string.device_has_no_accelerometer_exit));
            } else {
                mLastDialogNumber = 2;
            }
        }

        if (mLastDialogNumber == 2) {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

            if(telephonyManager == null){
                //error, no TELEPHONY_SERVICE in this device
                showExitDialog(this, getString(R.string.phone_service_is_not_available_description), getString(R.string.sim_is_not_available_positive_button));
                return;
            }
            //PHONE_TYPE_NONE - 0, GSM - 1, CDMA - 2, SIP - 3.
            int nTelephoneType = telephonyManager.getPhoneType();

            //@return the NETWORK_TYPE_xxxx for current data connection.
            //NETWORK_TYPE_UNKNOWN ! doesn't mean network is none !
            //Result may be unreliable on CDMA networks (TYPE 2)
            //int nTelephoneNetworkType = telephonyManager.getNetworkType();
            //@returns the numeric name (MCC+MNC) of current registered operator.
            //String sTelephoneOperator = telephonyManager.getNetworkOperator();

            if(nTelephoneType == TelephonyManager.PHONE_TYPE_NONE){
                //error, can't call\send sms
                showExitDialog(this, getString(R.string.device_can_not_call_description), getString(R.string.sim_is_not_available_positive_button));
                return;
            }

            int nSimState = telephonyManager.getSimState();

            if(nSimState != TelephonyManager.SIM_STATE_READY){
                //error, something wrong with SIM card
                showExitDialog(this, getString(R.string.sim_is_not_available_description), getString(R.string.sim_is_not_available_positive_button));
                return;
            }else{
                String sSimOperator = telephonyManager.getSimOperator();
                if(sSimOperator.isEmpty()){
                    //SIM is ready, but no operator to connect
                    showExitDialog(this, getString(R.string.sim_operator_is_empty_description), getString(R.string.sim_is_not_available_positive_button));
                    return;
                }
            }

            boolean bAirplaneMode = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1){
                /* API 17 and above */
                bAirplaneMode = Settings.Global.getInt(this.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
            } else {
                /* below */
                bAirplaneMode = Settings.System.getInt(this.getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0) != 0;
            }
            if(bAirplaneMode){
                showExitDialog(this, getString(R.string.airplane_mode_description), getString(R.string.sim_is_not_available_positive_button));
                return;
            }

            mLastDialogNumber = 3;
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

    public static void showOneButtonDialogAuthors(final Context context) {
        //working with strings of About dialog
        String sMessage = context.getString(R.string.authors_text);
        String sVersionName = BuildConfig.VERSION_NAME;
        sMessage = sMessage.replace("$version", sVersionName);

        final SpannableString sResult = new SpannableString(sMessage);

        Linkify.addLinks(sResult, Linkify.EMAIL_ADDRESSES);

        //creating view for AlertDialog
        final TextView messageTmp = new TextView(context);
        messageTmp.setText(sResult);
        messageTmp.setTextSize(TypedValue.COMPLEX_UNIT_PT, 12);
        messageTmp.setMovementMethod(LinkMovementMethod.getInstance());

        new AlertDialog.Builder(context)
                .setPositiveButton(R.string.authors_positive_button, null)
                .setView(messageTmp)
                .setTitle(R.string.authors_title)
                .setCancelable(true)
                //.setIcon(android.R.drawable.ic_dialog_info)
                .create()
                .show();
    }

    private boolean showDialogCheckLocationManager(){

        final LocationManager locationManager =
                (LocationManager) this.getSystemService(LOCATION_SERVICE);

        //checking ALL providers
        if(!locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
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
                                        mStatusSwitch.setChecked(false); //service mustn't run before this
                                        dialogInterface.cancel(); //will call DialogInterface.OnCancelListener
                                    }
                                }
                        )
                        .create()
                        .show();
                return false;
            }
        }

        return true;
    }

    public static void showExitDialog(final Activity activity, final String message, final String exitButtonText) {
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
                .setTitle(R.string.dialog_exit_title)
                .setIcon(R.drawable.close_red)
                .create()
                .show();
    }
    //for ContentProvider, in progress 2016.12
    /*
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
    //*/
}