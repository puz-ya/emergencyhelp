package evyasonov.emergencyhelp;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;


public class AlarmFragment extends Fragment implements View.OnClickListener {
    private static final String LOG_TAG = "evyasonov/AlarmFragment";

    private View mFragmentView;
    private RelativeLayout mLayout;
    private TextView mAlarmDescriptionView;
    private TextView mTimeLeftView;
    private Button mStopButton;

    private ChangeBackgroundTask mBackgroundChangeTask;
    private Vibrator mVibrator;
    private MediaPlayer mMediaPlayer;

    private long mWaitingTime;
    private long mStartTime;

    private Context mContext;
    private Activity mActivity;
    private MessengerToAccelerometerMonitoringService mService;

    private static final int MODE_PRE_ALARM = 1;
    private static final int MODE_ALARM = 2;
    private static final int MODE_POST_ALARM = 3;

    private int mCurrentMode = MODE_PRE_ALARM;

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        Log.d(LOG_TAG, "onAttach");

        mActivity = activity;

        if (mContext == null) {
            mContext = activity.getApplicationContext();
        }

        releaseActivityWindow();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate");

        mCurrentMode = MODE_PRE_ALARM;

        setRetainInstance(true);

        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        final AssetFileDescriptor alarmSound = getResources().openRawResourceFd(R.raw.alarm_sound);

        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
        mMediaPlayer.setLooping(true);
        try {
            mMediaPlayer.setDataSource(alarmSound.getFileDescriptor());
            //mMediaPlayer.prepare();
            // You will get Error (-38,0) IF you call mediaPlayer.start() before it has reached the prepared state.
            mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    // Do something. For example: playButton.setEnabled(true);
                    mp.start();
                }
            });
            mMediaPlayer.prepareAsync();

            alarmSound.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // TODO: Handle exception (if audio can't be loaded
        // TODO: check if mMediaManager is null

        mService = new MessengerToAccelerometerMonitoringService(mContext, mMessageHandler);
        mService.bindService();
    }

    @Override
    public View onCreateView(
            final LayoutInflater inflater,
            final ViewGroup container,
            final Bundle savedInstanceState
    ) {
        Log.d(LOG_TAG, "onCreateView");

        if (mFragmentView == null) {
            mFragmentView = inflater.inflate(R.layout.fragment_alarm, null);

            mLayout = (RelativeLayout) mFragmentView.findViewById(R.id.alarmLayout);
            mTimeLeftView = (TextView) mFragmentView.findViewById(R.id.timeLeft);
            mAlarmDescriptionView = (TextView) mFragmentView.findViewById(R.id.alarmDescription);

            mStopButton = (Button) mFragmentView.findViewById(R.id.stopButton);
            mStopButton.setOnClickListener(this);
        }

        return mFragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "onResume");

        if (mCurrentMode == MODE_PRE_ALARM) {
            mWaitingTime = 1000 * Integer.parseInt(
                    mContext
                            .getSharedPreferences(
                                    SettingsActivity.PREFERENCES_FILENAME,
                                    Context.MODE_MULTI_PROCESS)
                            .getString("alarm_time", "60"));
            mStartTime = System.currentTimeMillis();

            mMediaPlayer.start();

            mBackgroundChangeTask = new ChangeBackgroundTask();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                mBackgroundChangeTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } else {
                mBackgroundChangeTask.execute();
            }

            if (mVibrator.hasVibrator()) {
                long[] pattern = {0, 1000, 1000};
                mVibrator.vibrate(pattern, 0);
            }

            mCurrentMode = MODE_ALARM;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        Log.d(LOG_TAG, "onDetach");

        mActivity = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        releaseAlarmObjects();
        mService.unbindService();
    }


    @Override
    public void onClick(final View view) {
        // This method will be also called when the back button pressed
        stopAlarmByUser();
        getActivity().finish();
    }


    private void stopAlarmByUser() {
        mCurrentMode = MODE_POST_ALARM;
        releaseAlarmObjects();

        mService.sendMessageToService(AccelerometerMonitoringService.MSG_ALARM_CANCELLED);
    }

    private void stopAlarmByTimeout() {
        mCurrentMode = MODE_POST_ALARM;
        releaseAlarmObjects();

        mService.sendMessageToService(AccelerometerMonitoringService.MSG_ALARM_NOT_CANCELLED);

        mLayout.setBackgroundColor(Color.RED);
        mAlarmDescriptionView.setText(R.string.alarm_screen_messages_sent);
        mTimeLeftView.setText("");
        mStopButton.setText(R.string.alarm_screen_button_after_alarm);
        // TODO: check if windows is closed
    }

    private void releaseAlarmObjects() {
        Log.d(LOG_TAG, "releaseAlarmObjects");

        if (mVibrator.hasVibrator()) {
            mVibrator.cancel();
        }

        mMediaPlayer.release();

        if (mBackgroundChangeTask != null) {
            mBackgroundChangeTask.cancel(true);
        }

        releaseActivityWindow();
    }

    private void releaseActivityWindow() {
        if (mActivity != null && mCurrentMode == MODE_POST_ALARM) {
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            mActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private class ChangeBackgroundTask extends AsyncTask<Void, Void, Void> {
        private boolean isRed = false;

        @Override
        protected Void doInBackground(Void... voids) {
            while ((System.currentTimeMillis() - mStartTime < mWaitingTime) && !isCancelled()) {
                publishProgress();

                isRed = !isRed;
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    return null;
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            stopAlarmByTimeout();
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            super.onProgressUpdate(values);

            mLayout.setBackgroundColor(isRed ? Color.BLUE : Color.RED);
            String toText = ((int) (mWaitingTime - (System.currentTimeMillis() - mStartTime)) / 1000) + "";
            mTimeLeftView.setText(toText);
        }
    }

    //Получить от сервиса сообщение, список тел номеров, куда отправили смс и отобразить на экране.
    private Handler mMessageHandler = new Handler() {
        @Override
        public void handleMessage(final Message msg) {
//            TODO: get numbers from msg and show the message
//            switch (msg.what) {
//                case AccelerometerMonitoringService.MSG_SMS_WAS_SEND:
//                    break;
//                case AccelerometerMonitoringService.MSG_SAY_HELLO:
//                    Log.d(LOG_TAG, "Hello!");
//                    break;
//                default:
//                    super.handleMessage(msg);
//            }
        }
    };
}
