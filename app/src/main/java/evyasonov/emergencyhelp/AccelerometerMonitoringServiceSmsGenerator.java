package evyasonov.emergencyhelp;

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

        String tmpLocation = "";
        if(location != null){

            //TODO: const 6e5 ? 600 000s ? 1ms == 0.001s
            //TODO: timeZONES ?
            String locationMinutesPass = String.format("%.1f", (System.currentTimeMillis() - location.getTime()) / 6e5);
            String locationLatitude = String.format("%.6f", location.getLatitude());
            String locationLongitude = String.format("%.6f", location.getLongitude());

            String locationAccuracy = "";
            if(location.hasAccuracy()){
                locationAccuracy = res.getString(R.string.accuracy_sentence).replace("$accuracy", location.getAccuracy() + "");
            }

            tmpLocation = res.getString(R.string.location_sentence)
                    .replace("$minutes_pass", locationMinutesPass)
                    .replace("$latitude", locationLatitude)
                    .replace("$longitude", locationLongitude)
                    .replace("$accuracy_sentence", locationAccuracy)
            ;
        }

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
