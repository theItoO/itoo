package xyz.kumaraswamy.itoo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import xyz.kumaraswamy.itoox.ItooCreator;

import java.io.IOException;
import java.util.HashMap;

public class ItooService extends Service {

    public static final int MSG_APPLICATION_STOPPED = 1;

    private static final String TAG = "ItooService";

    private ItooCreator creator;
    private Data data;

    private final HashMap<String, String> actions = new HashMap<>();


    @Override
    public IBinder onBind(Intent intent) {
        mMessenger = new Messenger(new IncomingHandler(this));
        return mMessenger.getBinder();
    }

    static class IncomingHandler extends Handler {
        private final ItooService service;

        public IncomingHandler(ItooService service) {
            this.service = service;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_APPLICATION_STOPPED) {
                Log.d(TAG, "Message received, app stopped!");
                service.creator.onAppStopped();
            } else {
                super.handleMessage(msg);
            }
        }
    }

    Messenger mMessenger;


    @Override
    public void onCreate() {
        super.onCreate();
        data = new Data(this);
        try {
            data.put("running", "yes");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            data.put("running", "no");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started[]");
        if (!data.exists("screen")) return START_NOT_STICKY;

        try {
            foregroundInit();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Doraemon");

        if (wakeLock.isHeld()) wakeLock.release();
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(86_400_000L);

        String screen;
        String procedure;
        try {
            screen = data.get("screen");
            procedure = data.get("procedure");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try {
            creator = new ItooCreator(this,
                    procedure,
                    screen, true);

        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, "Error While Executing Procedure");
            throw new RuntimeException(e.getMessage() + " call [" + procedure + "]");
        }
        return START_STICKY;
    }

    private void foregroundInit() throws IOException {
        notificationChannel();
        startForeground(123321,
                new NotificationCompat.Builder(this, "ItooApple")
                        .setOngoing(true)
                        .setSmallIcon(Integer.parseInt(data.get("icon")))
                        .setContentIntent(
                                PendingIntent.getService(
                                        this,
                                        127,
                                        new Intent(), PendingIntent.FLAG_IMMUTABLE))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentTitle(data.get("notification_title"))
                        .setContentText(data.get("notification_subtitle"))
                        .build());
    }

    private void notificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    "ItooApple",
                    "ItooApple",
                    NotificationManager.IMPORTANCE_HIGH
            );
            serviceChannel.setSound(null, null);
            NotificationManager manager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Event()");
            try {
                String action = intent.getAction();
                String procedure = actions.get(action);

                creator.startProcedureInvoke(procedure);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    };
}
