package evyasonov.emergencyhelp;

import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.SmsManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

// TODO: monitor sim states
// TODO: try to send one more sms in 1 minute if location are not defined

public class AccelerometerMonitoringService extends Service {

    static final int MSG_ALARM_CANCELLED = 2;
    static final int MSG_ALARM_NOT_CANCELLED = 3;
    static final int MSG_PREFERENCES_WAS_CHANGED = 4;
    static final int MSG_SMS_WAS_SEND = 5;

    private static final String LOG_TAG = "evyasonov/service";

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


    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");

        readUserPreferences();

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        final PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_NAME);
        mWakeLock.acquire();

        applyNormalMode();

        setServiceIsRunningNotification();
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(final Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        Log.d(LOG_TAG, "onDestroy");

        mLocationManager.removeUpdates(mLocationListener);
        mSensorManager.unregisterListener(mAccelerometerListener, mSensor);

        //GPS ? освободить ?

        stopForeground(true);

        mWakeLock.release();

        super.onDestroy();
    }


    private void applyNormalMode() {
        Log.d(LOG_TAG, "applyNormalMode");

        mSensorManager.unregisterListener(mAccelerometerListener, mSensor);
        final boolean batchMode =
                mSensorManager.registerListener(mAccelerometerListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if(!batchMode){
            //cannot register listener -> exit
            //TODO: send a message that we are f***ed
            Log.d(LOG_TAG, "f***ed");
        }

        mLocationManager.removeUpdates(mLocationListener);
        mLocationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000 * 60 * GPS_FREQUENCY_IN_MINUTES,
                GPS_MIN_DISTANCE_SENSIVITY,
                mLocationListener);


        mLocationOnAlarmMoment = null;
    }

    private void startAlarm() {
        Log.d(LOG_TAG, "startAlarm");

        mSensorManager.unregisterListener(mAccelerometerListener, mSensor);

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, mLocationListener);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);

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
                                        this, 0, new Intent(this, MainActivity.class), 0));

        final NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(++mLastNotificationID, builder.build());
    }

    private void readUserPreferences() {
        Log.d(LOG_TAG, "readUserPreferences");

        final SharedPreferences sharedPreferences = getApplicationContext()
                .getSharedPreferences(SettingsActivity.PREFERENCES_FILENAME, MODE_MULTI_PROCESS);

        mSensorThreshold = 9.81 * Integer.parseInt(sharedPreferences.getString("alarm_sensitive", "4"));

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
        }

        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(icon)
                        .setContentTitle(getString(R.string.notification_title))
                        .setContentText(
                            getString(R.string.notification_description).replace("$GPS", gpsText)
                        );

        final PendingIntent notificationClickIntent =
                PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        builder.setContentIntent(notificationClickIntent);

        //todo: ??
        startForeground(1, builder.build());
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

        }

        //TODO: never used concatValues()
        private String concatValues(final float[] values) {
            return values[0] + " "  + values[1] + " " + values[2];
        }
    };

    private final MyLocationListener mLocationListener = new MyLocationListener();

// TODO: Кнопка "Добавить" должна быть видна везде
// TODO: Текст Автозагрузки не вмещается
// TODO: в смс лишние цифры

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    private final Messenger mMessenger = new Messenger(new Handler() {
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
                    stopSelf();
                    System.exit(0);
                    break;

                case MSG_PREFERENCES_WAS_CHANGED:
                    readUserPreferences();
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    });

    private class MyLocationListener implements LocationListener {
        private static final int SIGNIFICANT_TIME_IN_MILLIS = 1000 * 60 * 2;
        private static final int SIGNIFICANT_ACCURACY_IN_METERS = 10;

        private Location mBestLocation = null;

        @Override
        public void onLocationChanged(final Location location) {
            Log.d(LOG_TAG, "onLocationChanged: " + location.toString());

            if (isThisLocationBetter(location)) {
                mBestLocation = location;
            }
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
        }

        public Location getLastLocation() {
            return mBestLocation;
        }

        private boolean isThisLocationBetter(final Location newLocation) {
            if (newLocation == null) {
                return true;
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
            if (provider1 == null) {
                return provider2 == null;
            }
            return provider1.equals(provider2);
        }
    }


    public static boolean isServiceRunning(Context context) {
        final Class<?> serviceClass = AccelerometerMonitoringService.class;

        final ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (final ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}
