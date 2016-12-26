package e_and_y.emergencyhelp;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

// TODO: monitor sim states
// TODO: try to send one more sms in 1 minute if location are not defined

public class AccelerometerMonitoringService
        extends Service
        implements Loader.OnLoadCompleteListener<Cursor>
{

    static final int MSG_ALARM_CANCELLED = 2;
    static final int MSG_ALARM_NOT_CANCELLED = 3;
    static final int MSG_PREFERENCES_WAS_CHANGED = 4;
    static final int MSG_SMS_WAS_SEND = 5;

    static final int FOREGROUND_ID = 2020;

    private static final String LOG_TAG = "e.y/Acceler...Service";

    /**
     * How many events from sensor will be kept for determining alarm.
     */
    private static final int ACCELEROMETER_EVENTS_COUNT = 2;
    private static final int GPS_FREQUENCY_IN_MINUTES = 2;
    private static final int GPS_MIN_DISTANCE_SENSIVITY = 10;

    private static final String WAKE_LOCK_NAME = "EmergencyCallerWakeLock";

    private double mSensorThreshold = 4 * 9.81;


    private Sensor mSensor;
    private SensorManager mSensorManager;
    private LocationManager mLocationManager;

    private List<String> mPhoneNumbers;
    private String mUserName;
    private String mUserComment;

    private PowerManager.WakeLock mWakeLock;

    private int mLastNotificationID = 1;

    //TODO: never used mLocationOnAlarmMoment
    private Location mLocationOnAlarmMoment;

    //cotent provider loader
    private CursorLoader mCursorLoader;
    private int LOADER_LISTENER_ID = 1230;
    private CursorLoader mCursorLoaderPhones;
    private int LOADER_LISTENER_ID_PHONES = 1231;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        final PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
        mWakeLock.acquire();

        //Cursor for accessing new settings and stuff
        mCursorLoader = new CursorLoader(this, SettingsContentProvider.CONTENT_URI, null, null, null, null);
        mCursorLoader.registerListener(LOADER_LISTENER_ID, mLoaderSettings);
        mCursorLoader.startLoading();

        mCursorLoaderPhones = new CursorLoader(this, SettingsContentProvider.CONTENT_URI_PHONES, null, null, null, null);
        mCursorLoaderPhones.registerListener(LOADER_LISTENER_ID_PHONES, mLoaderPhones);
        mCursorLoaderPhones.startLoading();
        //*/
    } //*/


    Loader.OnLoadCompleteListener<Cursor> mLoaderSettings = new Loader.OnLoadCompleteListener<Cursor>(){
        @Override
        public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor){
            Log.d(LOG_TAG, "onLoadCompleteSettingsCursor");
            if(cursor != null) {
                cursor.moveToFirst();
                StringBuilder res = new StringBuilder();

                //android.os.Debug.waitForDebugger();
                while (!cursor.isAfterLast()) {
                    res.append("\n"
                            + cursor.getString(cursor.getColumnIndex("name"))
                            + " - "
                            + cursor.getString(cursor.getColumnIndex("name2"))
                    );
                    cursor.moveToNext();
                }

                //mResultView.setText(res);

                Log.d(LOG_TAG, res.toString());
            }
        }
    };

    Loader.OnLoadCompleteListener<Cursor> mLoaderPhones = new Loader.OnLoadCompleteListener<Cursor>(){
        @Override
        public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor){
            Log.d(LOG_TAG, "onLoadCompletePhonesCursor");
            //TODO: set loader for phone contacts
        }
    };

    @Override
    public void onLoadComplete(Loader<Cursor> arg0, Cursor cursor){
        //not using it, but necessary for "implements Loader.OnLoadCompleteListener<Cursor>"
        //we are using mLoaderSettings, mLoaderPhones
    }

    public int onStartCommand(Intent intent, int flags, int startId){
        //ENABLE DEBUG in SERVICE FUUUUUUUUUCK
        //android.os.Debug.waitForDebugger();  // this line is key
        Log.d(LOG_TAG, "onStartCommand");
        //Toast.makeText(this, "Service Started", Toast.LENGTH_LONG).show();

        readUserPreferences();

        Log.d(LOG_TAG, "onStartCommand_applyNormalMode");
        applyNormalMode();

        Log.d(LOG_TAG, "onStartCommand_setServiceIsRunningNotification");
        setServiceIsRunningNotification();

        //Sticky – A sticky service will be restarted, and a null intent will be delivered to OnStartCommand at restart.
        // Used when the service is continuously performing a long-running operation, such as updating a stock feed.
        //Log.d(LOG_TAG, "onStartCommand_return");
        return START_STICKY;
        //return START_REDELIVER_INTENT;    //the last intent that was delivered to OnStartCommand before the service was stopped
    } //*/

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(final Intent intent) {
        Log.d(LOG_TAG, "onBind");
        return mMessenger.getBinder();
        //return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        //Toast.makeText(this, "onUnbind", Toast.LENGTH_LONG).show();
        Log.d(LOG_TAG, "onUnbind");
        return false;    // true -> indicates whether onRebind should be used
    }
    @Override
    public void onRebind(Intent intent) {
        Log.d(LOG_TAG, "onRebind");
        //Toast.makeText(this, "onRebind", Toast.LENGTH_LONG).show();
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }

    //When we remove app from application list, onTaskRemoved calls for service
    @Override
    public void onTaskRemoved(Intent rootIntent){
        //Toast.makeText(getApplicationContext(), "<< onTaskRemoved called >>", Toast.LENGTH_LONG).show();
        Log.d(LOG_TAG, "onTaskRemoved");

        // start blank activity to prevent kill
        // @see https://code.google.com/p/android/issues/detail?id=53313
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroyAccelService");

        mLocationManager.removeUpdates(mLocationListener);
        mSensorManager.unregisterListener(mAccelerometerListener, mSensor);

        //TODO: GPS ? освободить ?

        //android.os.Debug.waitForDebugger();  // this line is key
        //Toast.makeText(getApplicationContext(), "STOP FOREGROUND", Toast.LENGTH_LONG).show();
        stopForeground(true);

        mWakeLock.release();

        if(mCursorLoader != null){
            mCursorLoader.unregisterListener(mLoaderSettings);
            mCursorLoader.cancelLoad();
            mCursorLoader.stopLoading();
        }
        if(mCursorLoaderPhones != null){
            mCursorLoaderPhones.unregisterListener(mLoaderPhones);
            mCursorLoaderPhones.cancelLoad();
            mCursorLoaderPhones.stopLoading();
        }

        super.onDestroy();
        //*/
    }


    private void applyNormalMode() {
        //ENABLE DEBUG in SERVICE FUUUUUUUUUCK
        //android.os.Debug.waitForDebugger();  // this line is key

        Log.d(LOG_TAG, "applyNormalMode");

        mSensorManager.unregisterListener(mAccelerometerListener, mSensor);
        final boolean batchMode =
                mSensorManager.registerListener(mAccelerometerListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        /*if(!batchMode){
            //cannot register listener -> exit
            //TODO: send a message that we are f***ed
            Log.d(LOG_TAG, "f***ed");
            return;
        }
        //*/

        mLocationManager.removeUpdates(mLocationListener);
        mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, //1000 * 60 * GPS_FREQUENCY_IN_MINUTES,
                GPS_MIN_DISTANCE_SENSIVITY,
                mLocationListener);


        mLocationOnAlarmMoment = null;
        //*/
    }

    private void startAlarm() {
        Log.d(LOG_TAG, "startAlarm");

        mSensorManager.unregisterListener(mAccelerometerListener, mSensor);

        //first - checking Network (wifi), usually disabled
        String provider = LocationManager.NETWORK_PROVIDER;
        if (mLocationManager.isProviderEnabled(provider)) {
            mLocationManager.requestLocationUpdates(provider, 0, 0, mLocationListener);
        }

        //second - checking GPS, usually auto-enabled
        provider = LocationManager.GPS_PROVIDER;
        if (mLocationManager.isProviderEnabled(provider)) {
            mLocationManager.requestLocationUpdates(provider, 0, 0, mLocationListener);
        }

        mLocationOnAlarmMoment = mLocationListener.getLastLocation();

        final Intent intent = new Intent(getApplicationContext(), AlarmActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    private void sendSMSMessages() {
        Log.d(LOG_TAG, "sendSMSMessages");

        final SmsManager smsManager = SmsManager.getDefault();
        final String message = AccelerometerMonitoringServiceSmsGenerator.generate(
                getApplicationContext(),
                mUserName,
                mUserComment,
                mLocationListener.getLastLocation() );

        final ArrayList<String> parts = smsManager.divideMessage(message);

        for (final String number : mPhoneNumbers) {
            smsManager.sendMultipartTextMessage(number, null, parts, null, null);
        }

        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ic_stat_notify_ok)
                        .setContentTitle(getString(R.string.notification_sms_was_sent))
                        .setContentText(getString(R.string.notification_sms_was_sent) + mPhoneNumbers)
                        .setAutoCancel(true)
                        .setContentIntent(
                                PendingIntent.getActivity(
                                        this, 1, new Intent(this, MainActivity.class), 0));

        final NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(++mLastNotificationID, builder.build());
    }

    private void readUserPreferences() {
        Log.d(LOG_TAG, "readUserPreferences");

        final SharedPreferences sharedPreferences = getApplicationContext()
                .getSharedPreferences(SettingsActivity.PREFERENCES_FILENAME, MODE_MULTI_PROCESS);

        //must check for input values
        String sAlarmSensitive = sharedPreferences.getString("alarm_sensitive", "3");
        if(sAlarmSensitive.contains(".") || sAlarmSensitive.contains(",") || sAlarmSensitive.contains("-")){
            sAlarmSensitive = "3";
        }
        mSensorThreshold = 9.81 * Integer.parseInt(sAlarmSensitive);

        mPhoneNumbers = new LinkedList<String>();

        final Set<String> emergencyContacts =
                sharedPreferences.getStringSet("emergency_contacts", new TreeSet<String>());

        for (final String emergencyContact : emergencyContacts) {
            final String number =
                    SettingsFragmentPhones.getStripedPhoneNumberFromContact(emergencyContact);

            if (PhoneNumberUtils.isWellFormedSmsAddress(number) && !mPhoneNumbers.contains(number)) {
                mPhoneNumbers.add(number);
            }
        }

        mUserName = sharedPreferences.getString("user_name", "");
        mUserComment = sharedPreferences.getString("user_comment", "");
    }

    // todo: delete detResources
    private void setServiceIsRunningNotification() {
        Log.d(LOG_TAG, "setServiceIsRunningNotification");

        String gpsText = getString(R.string.notification_gps_off);
        int icon = R.drawable.ic_stat_notify_warning;
        if (mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            gpsText = getString(R.string.notification_gps_on);
            icon = R.drawable.ic_stat_notify_ok;
        }else{
            //checking our last hope - Network provider
            if (mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                gpsText = getString(R.string.notification_network_on);
                icon = R.drawable.ic_stat_notify_ok;
            }else{
                Log.d(LOG_TAG, "GPS|NETWORK is OFF :(");
                Toast.makeText(this,"GPS|NETWORK is OFF :(",Toast.LENGTH_SHORT).show();
            }
        }

        String sLocationNotification = ", Loc: ";
        Location location = mLocationListener.getLastLocation();
        if(location != null){
            sLocationNotification += location.getLatitude();
            sLocationNotification += ", ";
            sLocationNotification += location.getLongitude();
        }else{
            icon = R.drawable.ic_stat_notify_warning;
            sLocationNotification += "Unknown :(";
        }

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        //API 20+ with Material Design set background and colors to WHITE FUCK, choose wisely
        //TODO: icon API 20+
        builder.setSmallIcon(icon); //in Status Bar small icon
        Bitmap largeNotifyIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_stat_notify_ok);
        builder.setLargeIcon(largeNotifyIcon); //after user pull down notification show LARGE one

        builder.setContentTitle(getString(R.string.notification_title));
        builder.setContentText(getString(R.string.notification_description).replace("$GPS", gpsText + sLocationNotification));

        //Chinese XIAOMI FUCK, they changed icons in status bar to app-icon (NOT notifications!)
        //notification is an object of class android.app.Notification
        /*
        try {
            Class miuiNotificationClass = Class.forName("android.app.MiuiNotification");
            Object miuiNotification = miuiNotificationClass.newInstance();
            Field field = miuiNotification.getClass().getDeclaredField("customizedIcon");
            field.setAccessible(true);

            field.set(miuiNotification, true);
            field = builder.getClass().getField("extraNotification");
            field.setAccessible(true);

            field.set(builder, miuiNotification);
        } catch (Exception e) {
            Log.d(LOG_TAG, "miuiException" + e.getMessage());
        }
        //*/

        Log.d(LOG_TAG, "notificationIntent");

        Intent notificationIntent = new Intent(this, MainActivity.class);
        //Hack to not close Service from app list
        if(Build.VERSION.SDK_INT >= 16){     //The flag we used here was only added at API 16
            notificationIntent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        }
        final PendingIntent notificationClickIntent = PendingIntent.getActivity(this, 1, notificationIntent, 0);
        builder.setContentIntent(notificationClickIntent);

        Log.d(LOG_TAG, "startForeground");
        startForeground(FOREGROUND_ID, builder.build());
    }


    /**
     * Listener that handles accelerometer sensor events.
     */
    private final SensorEventListener mAccelerometerListener = new SensorEventListener() {
        private final Queue<float[]> mValues = new LinkedList<float[]>();

        @Override
        public void onSensorChanged(final SensorEvent event) {

//            Log.d(LOG_TAG, "values(0): " + concatValues(event.values));

            if (mValues.size() > ACCELEROMETER_EVENTS_COUNT) {
                mValues.poll();
            }

            boolean clearHistory = false;
            for (final float[] value : mValues) {
//                Log.d(LOG_TAG, "values(-1): " + concatValues(value));
                if (
                        (Math.abs(value[0] - event.values[0]) > mSensorThreshold)
                                ||
                                (Math.abs(value[1] - event.values[1]) > mSensorThreshold)
                                ||
                                (Math.abs(value[2] - event.values[2]) > mSensorThreshold) )
                {
//                    Log.d(LOG_TAG, "ALARM");
                    startAlarm();
                    clearHistory = true;
                    break;
                }
            }

            if (clearHistory) {
                mValues.clear();
                return;
            }

            mValues.add(event.values.clone());
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
            //TODO: onAccuracyChanged() empty
        }

        //TODO: never used concatValues()
        private String concatValues(final float[] values) {
            return values[0] + " "  + values[1] + " " + values[2];
        }
    };

// TODO: в смс лишние цифры

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
            Log.d(LOG_TAG, "handleMessage");

            switch (msg.what) {
                case MSG_ALARM_CANCELLED:
                    applyNormalMode();
                    break;

                case MSG_ALARM_NOT_CANCELLED:
                    sendSMSMessages();
                    try {
                        msg.replyTo.send(Message.obtain(null, MSG_SMS_WAS_SEND, 0, 0));
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    stopSelf();     //stop service after sms sent
                    System.exit(0);
                    break;

                case MSG_PREFERENCES_WAS_CHANGED:
                    readUserPreferences();
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    };
    private final Messenger mMessenger = new Messenger(mHandler);

    //set new LocationListener class
    private final MyLocationListener mLocationListener = new MyLocationListener();

    private class MyLocationListener implements LocationListener {
        private static final int SIGNIFICANT_TIME_IN_MILLIS = 1000 * 60;    //1 minutes
        private static final int SIGNIFICANT_ACCURACY_IN_METERS = 50;

        private Location mBestLocation = null;

        @Override
        public void onLocationChanged(final Location location) {
            Log.d(LOG_TAG, "onLocationChanged: " + location.toString());

            if (isThisLocationBetter(location)) {
                mBestLocation = location;
            }
            setServiceIsRunningNotification();
        }

        @Override
        public void onProviderDisabled(final String provider) {
            Log.d(LOG_TAG, "GPS OFF");
            setServiceIsRunningNotification();
        }

        @Override
        public void onProviderEnabled(final String provider) {
            Log.d(LOG_TAG, "GPS ON");
            setServiceIsRunningNotification();
        }

        @Override
        public void onStatusChanged(final String provider, final int status, final Bundle extras) {
            Log.d(LOG_TAG, "GPS STATUS");
            setServiceIsRunningNotification();
        }

        public Location getLastLocation() {
            return mBestLocation;
        }

        private boolean isThisLocationBetter(final Location newLocation) {

            Log.d(LOG_TAG, "isLocationBetter");

            //first checking current\old location
            if(mBestLocation == null){
                mBestLocation = new Location("test_provider");//provider name is unecessary
                mBestLocation.setLatitude(0.0d);//coords of course
                mBestLocation.setLongitude(0.0d);
                mBestLocation.setTime(1479156000);  //2016-11-14T20:40:00
                mBestLocation.setAccuracy(SIGNIFICANT_ACCURACY_IN_METERS * 10); //10 times more than needed
            }

            //now checking is there some new location
            if (newLocation == null) {
                return false;
            }

            // Check whether the new location fix is newer or older
            final long timeDelta = newLocation.getTime() - mBestLocation.getTime();
            final boolean isSignificantlyNewer = timeDelta > SIGNIFICANT_TIME_IN_MILLIS;
            final boolean isSignificantlyOlder = timeDelta < -SIGNIFICANT_TIME_IN_MILLIS;
            final boolean isNewer = timeDelta > 0;

            if (isSignificantlyNewer) {
                return true;
            } else if (isSignificantlyOlder) {
                return false;
            }

            // Check whether the new location fix is more or less accurate
            final int accuracyDelta = (int) (newLocation.getAccuracy() - mBestLocation.getAccuracy());
            final boolean isLessAccurate = accuracyDelta > 0;
            final boolean isMoreAccurate = accuracyDelta < 0;
            final boolean isSignificantlyLessAccurate = accuracyDelta > SIGNIFICANT_ACCURACY_IN_METERS;

            // Check if the old and new location are from the same provider
            final boolean isFromSameProvider = isSameProvider(newLocation.getProvider(),
                    mBestLocation.getProvider());

            // Determine location quality using a combination of timeliness and accuracy
            if (isMoreAccurate) {
                return true;
            } else if (isNewer && !isLessAccurate) {
                return true;
            } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
                return true;
            }
            return false;
        }

        /** Checks whether two providers are the same */
        private boolean isSameProvider(final String provider1, final String provider2) {
            Log.d(LOG_TAG, "isSameProvider");
            if (provider1 == null) {
                return provider2 == null;
            }
            return provider1.equals(provider2);
        }
    }


    public static boolean isServiceRunning(Context context) {
        Log.d(LOG_TAG, "isServiceRunning");
        final Class<?> serviceClass = AccelerometerMonitoringService.class;

        final ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (final ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.d(LOG_TAG, "isServiceRunningTRUE");
                return true;
            }
        }
        Log.d(LOG_TAG, "isServiceRunningFALSE");
        return false;
    }

}
