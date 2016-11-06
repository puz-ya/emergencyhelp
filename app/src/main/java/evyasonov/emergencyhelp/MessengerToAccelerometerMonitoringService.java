package evyasonov.emergencyhelp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import java.util.LinkedList;
import java.util.Queue;


public class MessengerToAccelerometerMonitoringService {
    /** Flag indicating whether we have called bind on the service. */
    private boolean mBound;
    private boolean mIsServiceRunning = false;

    /** Messengers for communicating with the service. */
    private Messenger mToService;
    private Messenger mFromService;

    private final Queue<Integer> mUnsentMessages = new LinkedList<Integer>();

    private Context mContext;


    public MessengerToAccelerometerMonitoringService(final Context context, final Handler messageHandler) {
        mContext = context;

        if (messageHandler == null) {
            mFromService = new Messenger(new Handler());
        } else {
            mFromService = new Messenger(messageHandler);
        }
    }

    public void sendMessageToService(final int msgCode) {
        if (mIsServiceRunning) {
            if (mBound) {
                final Message msg = Message.obtain(null, msgCode, 0, 0);
                msg.replyTo = mFromService;
                try {
                    mToService.send(msg);
                } catch (RemoteException e) {
                    mBound = false;
                    mUnsentMessages.add(msgCode);
                }
            } else {
                mUnsentMessages.add(msgCode);
            }
        }
    }

    public void bindService() {
        if (!mIsServiceRunning) {
            mIsServiceRunning = AccelerometerMonitoringService.isServiceRunning(mContext);
            if (mIsServiceRunning) {
                mContext.bindService(
                        new Intent(mContext, AccelerometerMonitoringService.class),
                        mConnection,
                        0);
            }
        }
    }

    public void unbindService() {

        /* Because mContext live even when activity destroys including this object.
         * And this object contains link on mConnection which uses in bindService which is method
         * of mContext. And this method waits until service will be launched. And when the service
         * starts onServiceConnected is called and all unsent messages is sent to service. */
        mUnsentMessages.clear();

        if (mBound) {
            mContext.unbindService(mConnection);
            mBound = false;
        }
    }

    public boolean isBound() {
        return mBound;
    }


    /**
     * Class for interacting with the main interface of the service.
     */
    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(final ComponentName className, final IBinder service) {
            mToService = new Messenger(service);
            mBound = true;

            while (!mUnsentMessages.isEmpty()) {
                if (!mBound) {
                    return;
                }
                sendMessageToService(mUnsentMessages.remove());
            }
        }

        public void onServiceDisconnected(final ComponentName className) {
            mToService = null;
            mBound = false;
            mIsServiceRunning = AccelerometerMonitoringService.isServiceRunning(mContext);
        }
    };

}
