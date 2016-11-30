package e_and_y.emergencyhelp;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Created by YD on 06.11.2016.
 */


/*static*/ class AccelerometerMonitoringServiceSmsGenerator {


    static String generate(final Context context, final String userName, final String userComment, final Location location) {
        final Resources res = context.getResources();

        String tmpUserName = userName.equals("") ? "" : res.getString(R.string.name_sentence).replace("$name", userName);
        String tmpAdditionalInfo = userComment.equals("") ? "" : res.getString(R.string.addition_info_sentence).replace("$addition_info", userComment);

        String locationMinutesPass, locationLatitude, locationLongitude,
                locationAccuracy = "";  //needed to be initilized
        if(location != null){

            //TODO: const 6e5 ? 600 000s ? 1ms == 0.001s
            //TODO: timeZONES ?
            locationMinutesPass = String.format(Locale.US, "%.1f", (System.currentTimeMillis() - location.getTime()) / 6e5);
            locationLatitude = String.format(Locale.US, "%.6f", location.getLatitude());
            locationLongitude = String.format(Locale.US, "%.6f", location.getLongitude());

            if(location.hasAccuracy()){
                locationAccuracy = res.getString(R.string.accuracy_sentence).replace("$accuracy", location.getAccuracy() + "");
            }

        }else{
            locationMinutesPass = "?GPS?";
            locationLatitude = "?GPS?";
            locationLongitude = "?GPS?";
            locationAccuracy = res.getString(R.string.accuracy_sentence).replace("$accuracy", "???");
        }

        String tmpLocation = res.getString(R.string.location_sentence)
                .replace("$minutes_pass", locationMinutesPass)
                .replace("$latitude", locationLatitude)
                .replace("$longitude", locationLongitude)
                .replace("$accuracy_sentence", locationAccuracy)
        ;

        //get current time HH:MM:SS
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        String tmpTime = sdf.format(date);

        return res.getString(R.string.message_template)
                .replace( "$name_sentence", tmpUserName )
                .replace( "$addition_info_sentence", tmpAdditionalInfo )
                .replace( "$location_sentence", tmpLocation )
                .replace( "$time", tmpTime );
    }
}
