// Copyright (C) 2023 Kumaraswamy B G
// GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007
// See LICENSE for full details
package xyz.kumaraswamy.itoo;

import android.app.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import org.json.JSONException;
import xyz.kumaraswamy.itoox.Framework;
import xyz.kumaraswamy.itoox.ItooInt;
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
      } catch (Throwable ignored) {

      }
      service.stopService();
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

  private void stopService() {
    try {
      framework.close();
      stopForeground(Service.STOP_FOREGROUND_REMOVE);
    } catch (Exception e) {
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
    Log.d(TAG, "onDestroy");
    stopService();
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

    final String serviceType = (String) data.read("service_type", "specialuse");
    Log.d(TAG, "Service Type: " + serviceType);
    final int serviceTypeCode = getServiceTypeCode(serviceType);

    //noinspection TryWithIdenticalCatches
    try {
      foregroundInit(serviceTypeCode);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (JSONException e) {
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

  private static int getServiceTypeCode(String serviceType) {
    if ("camera".equals(serviceType)) {
      return 64;
    } else if ("connecteddevice".equals(serviceType)) {
      return 16;
    } else if ("datasync".equals(serviceType)) {
      return 1;
    } else if ("health".equals(serviceType)) {
      return 256;
    } else if ("location".equals(serviceType)) {
      return 8;
    } else if ("mediaplayback".equals(serviceType)) {
      return 2;
    } else if ("mediaprocessing".equals(serviceType)) {
      return 8192;
    } else if ("mediaprojection".equals(serviceType)) {
      return 32;
    } else if ("microphone".equals(serviceType)) {
      return 128;
    } else if ("phonecall".equals(serviceType)) {
      return 4;
    } else if ("shortservice".equals(serviceType)) {
      return 2048;
    } else if ("remotemessaging".equals(serviceType)) {
      return 512;
    } else if ("systemexempted".equals(serviceType)) {
      return 1024;
    } else if ("specialuse".equals(serviceType)) {
      return 1073741824;
    } else {
      Log.d(TAG, "Unknown service type, defaulting to specialUse");
      return 1073741824; // special use
    }
  }

  private void foregroundInit(int serviceType) throws IOException, ClassNotFoundException, JSONException {
    notificationChannel();
    ItooInt itooInt = new ItooInt(this, "Screen1");
    Intent intent = new Intent(this, Class.forName(itooInt.getScreenPkgName("Screen1")));
    PendingIntent pd = PendingIntent.getActivity(this, 127, intent, PendingIntent.FLAG_IMMUTABLE);
    Notification notification = new NotificationCompat.Builder(this, "ItooApple")
            .setOngoing(true)
            .setSmallIcon(Integer.parseInt((String) data.read("icon", "-1")))
            .setContentIntent(pd)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentTitle((String) data.read("notification_title", "Itoo X"))
            .setContentText((String) data.read("notification_subtitle", "Itoo X"))
            .build();
    int notificationId = 123321;
    if (Build.VERSION.SDK_INT >= 34) {
      // we have to specify a service type
      startForeground(notificationId, notification, serviceType);
    } else {
      startForeground(notificationId, notification);
    }
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
