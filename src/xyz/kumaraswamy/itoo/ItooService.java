package xyz.kumaraswamy.itoo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import xyz.kumaraswamy.itoox.ItooCreator;

public class ItooService extends Service {

    private static final String TAG = "ItooService";

    private ItooCreator creator;


    private String[] actions;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service Started[]");

        String notifTitle = intent.getStringExtra("notification_title");
        String notifSubtitle = intent.getStringExtra("notification_subtitle");

        foregroundInit(notifTitle, notifSubtitle);

        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Doraemon");

        if (wakeLock.isHeld()) wakeLock.release();
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(86_400_000L);

        String screen = intent.getStringExtra("screen");
        String procedure = intent.getStringExtra("procedure");

        try {
            creator = new ItooCreator(this,
                    procedure,
                    screen, true);
        } catch (Throwable e) {
            Log.e(TAG, "Error While Executing Procedure");
            throw new RuntimeException(e);
        }
        actions = intent.getStringArrayExtra("actions");
        if (actions == null || actions[0] == null) return START_STICKY;

        IntentFilter filter = new IntentFilter(actions[0]);
        registerReceiver(receiver, filter);
        return START_STICKY;
    }

    private void foregroundInit(String notifTitle, String notifSubtitle) {
        notificationChannel();
        startForeground(123321,
                        new NotificationCompat.Builder(this, "ItooApple")
                        .setOngoing(true)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentIntent(
                                PendingIntent.getService(
                                        this,
                                        127,
                                        new Intent(), PendingIntent.FLAG_IMMUTABLE))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setContentTitle(notifTitle)
                        .setContentText(notifSubtitle)
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
                creator.startProcedureInvoke(actions[1]);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    };
}
