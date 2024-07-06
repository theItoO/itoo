// Copyright (C) 2023 Kumaraswamy B G
// GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007
// See LICENSE for full details
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
import org.json.JSONException;
import xyz.kumaraswamy.itoox.Framework;
import xyz.kumaraswamy.itoox.ItooPreferences;

import java.io.IOException;

public class ItooService extends Service {

  private static final String TAG = "ItooService";

  public static final String END_ACTION = "itoo_end_process";

  public static class EndActionReceiver extends BroadcastReceiver {

    private final ItooService service;

    public EndActionReceiver(ItooService service) {
      this.service = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
      String packageName = intent.getStringExtra("packageName");
      // sometimes we may receive broadcasts from other actions
      if (!packageName.equals(context.getPackageName())) {
        Log.d(TAG, "Ignoring stop call, package not matches");
        return;
      }
      Log.d(TAG, "Received Itoo End Process Action");
      try {
        service.unregisterReceiver(this);
        service.framework.close();
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private final EndActionReceiver endActionReceiver = new EndActionReceiver(this);

  private Framework framework;

  private ItooPreferences data;


  @Override
  public void onCreate() {
    super.onCreate();
    data = new ItooPreferences(this);
    try {
      data.write("running", "yes");
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    try {
      data.write("running", "no");
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d(TAG, "Service Started[]");
    if (!data.contains("screen")) {
      return START_NOT_STICKY;
    }

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

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // with flag "receiver isn't exported" for API levels 26 onwards
      registerReceiver(endActionReceiver, new IntentFilter(END_ACTION), 4);
    } else {
      registerReceiver(endActionReceiver, new IntentFilter(END_ACTION));
    }

    final String screen = (String) data.read("screen", "");
    final String procedure = (String) data.read("procedure", "");

    Framework.FrameworkResult result = Framework.get(this, screen);
    if (result.success()) {
      framework = result.getFramework();
      Framework.CallResult call = framework.call(procedure, 0);
      if (call.success()) {
        Log.d(TAG, "Call Was Successful");
      } else {
        Log.d(TAG, "Call was missed " + call.getThrowable());
      }
    } else {
      Log.d(TAG, "Unable to create framework " + result.getThrowable());
    }
    return START_STICKY;
  }

  private void foregroundInit() throws IOException {
    notificationChannel();
    startForeground(123321,
        new NotificationCompat.Builder(this, "ItooApple")
            .setOngoing(true)
            .setSmallIcon(Integer.parseInt((String) data.read("icon", "-1")))
            .setContentIntent(
                PendingIntent.getService(
                    this,
                    127,
                    new Intent(), PendingIntent.FLAG_IMMUTABLE))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle((String) data.read("notification_title", "Itoo X"))
            .setContentText((String) data.read("notification_subtitle", "Itoo X"))
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

}
