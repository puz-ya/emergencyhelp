package e_and_y.emergencyhelp;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class AlarmActivity extends Activity /*implements View.OnClickListener */{
    private Fragment mAlarmFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        final FragmentManager fragmentManager = getFragmentManager();
        mAlarmFragment = fragmentManager.findFragmentByTag("alarm");

        // If the Fragment is non-null, then it is currently being
        // retained across a configuration change.
        if (mAlarmFragment == null) {
            mAlarmFragment = new AlarmFragment();
            fragmentManager
                    .beginTransaction()
                    .replace(android.R.id.content, mAlarmFragment, "alarm")
                    .commit();
        }

        //Checking for test alarm
        Intent intent = getIntent();
        boolean bIsTest = intent.getBooleanExtra("IsTestClick", false);
        if(bIsTest){
            ((AlarmFragment) mAlarmFragment).setAlarmDescriptionView(true);
        }
    }

    @Override
    public void onBackPressed() {
        ((View.OnClickListener) mAlarmFragment).onClick(null);

        super.onBackPressed();
    }
}
