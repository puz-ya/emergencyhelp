package e_and_y.emergencyhelp;

/**
 * Created by YD on 30.11.2016.
 */

import java.util.Timer;
import java.util.TimerTask;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;


public class MyLocation {

    private Timer mTimer;

    LocationManager mLocationManager;
    LocationResult mLocationResult;

    private boolean mGpsEnabled = false;
    private boolean mNetworkEnabled=false;

    private static final String LOG_TAG = "e.y/MyLocation";


    public boolean getLocation(Context context, LocationResult result)
    {
        Log.d(LOG_TAG, "getLocation");
        //I use LocationResult callback class to pass location value from MyLocation to user code.
        mLocationResult = result;
        if(mLocationManager == null) {
            mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        }

        //exceptions will be thrown if provider is not permitted.
        try{
            mGpsEnabled=mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        }catch(Exception ex){
            Log.d(LOG_TAG, "GPS provider failed");
        }
        try{
            mNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }catch(Exception ex){
            Log.d(LOG_TAG, "Network provider failed");
        }

        //don't start listeners if no provider is enabled
        if(!mGpsEnabled && !mNetworkEnabled) {
            return false;
        }

        if(mGpsEnabled) {
            mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListenerGps);
        }
        if(mNetworkEnabled) {
            mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListenerNetwork);
        }
        mTimer=new Timer();
        mTimer.schedule(new GetLastLocation(), 5000);
        return true;
    }

    LocationListener locationListenerGps = new LocationListener() {
        public void onLocationChanged(Location location) {
            mTimer.cancel();
            mLocationResult.gotLocation(location);
            mLocationManager.removeUpdates(this);
            mLocationManager.removeUpdates(locationListenerNetwork);
        }
        public void onProviderDisabled(String provider) {}
        public void onProviderEnabled(String provider) {}
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    LocationListener locationListenerNetwork = new LocationListener() {
        public void onLocationChanged(Location location) {
            mTimer.cancel();
            mLocationResult.gotLocation(location);
            mLocationManager.removeUpdates(this);
            mLocationManager.removeUpdates(locationListenerGps);
        }
        public void onProviderDisabled(String provider) {}
        public void onProviderEnabled(String provider) {}
        public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    class GetLastLocation extends TimerTask {
        @Override
        public void run() {
            Log.d(LOG_TAG, "run");
            mLocationManager.removeUpdates(locationListenerGps);
            mLocationManager.removeUpdates(locationListenerNetwork);

            Location net_loc=null, gps_loc=null;
            if(mGpsEnabled) {
                gps_loc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if(mNetworkEnabled) {
                net_loc = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            //if there are both values use the latest one
            if(gps_loc!=null && net_loc!=null){
                if(gps_loc.getTime() > net_loc.getTime()) {
                    mLocationResult.gotLocation(gps_loc);
                }else {
                    mLocationResult.gotLocation(net_loc);
                }
                return;
            }

            if(gps_loc!=null){
                mLocationResult.gotLocation(gps_loc);
                return;
            }

            if(net_loc!=null){
                mLocationResult.gotLocation(net_loc);
                return;
            }

            mLocationResult.gotLocation(null);
        }
    }

    public void cancelTimer() {
        Log.d(LOG_TAG, "cancelTimer");
        mTimer.cancel();
        mLocationManager.removeUpdates(locationListenerGps);
        mLocationManager.removeUpdates(locationListenerNetwork);
    }

    public static abstract class LocationResult{
        public abstract void gotLocation(Location location);
    }
}