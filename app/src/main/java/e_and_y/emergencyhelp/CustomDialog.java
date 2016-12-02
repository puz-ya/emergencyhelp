package e_and_y.emergencyhelp;

import android.app.AlertDialog;
import android.content.Context;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.widget.TextView;

/**
 * Created by YD on 02.12.2016.
 *
 * Create different kind of dialogs (example: with emails of app authors).
 * usage: CustomDialog.create(this).show();
 *
 * in cause we will have TOO MUCH dialog methods
 */

class CustomDialog {

    AlertDialog mAlertDialog = null;

    AlertDialog createAuthorsDialog(Context context) {

        //working with strings of About dialog
        String sMessage = context.getString(R.string.authors_text);
        String sVersionName = BuildConfig.VERSION_NAME;
        sMessage = sMessage.replace("$version", sVersionName);

        final SpannableString sResult = new SpannableString(sMessage);

        Linkify.addLinks(sResult, Linkify.EMAIL_ADDRESSES);

        //creating view for AlertDialog
        final TextView message = new TextView(context);
        message.setText(sResult);
        message.setTextSize(TypedValue.COMPLEX_UNIT_PT, 12);
        message.setMovementMethod(LinkMovementMethod.getInstance());

        mAlertDialog = new AlertDialog.Builder(context)
                .setPositiveButton(R.string.authors_positive_button, null)
                .setView(message)
                .setTitle(R.string.authors_title)
                .setCancelable(true)
                //.setIcon(android.R.drawable.ic_dialog_info)
                .create();

        return mAlertDialog;
    }
}
