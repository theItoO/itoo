package xyz.kumaraswamy.itoo.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import twitter4j.internal.org.json.JSONException;
import xyz.kumaraswamy.itoo.ItooService;
import xyz.kumaraswamy.itoox.ItooPreferences;

import java.io.IOException;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d("BootItoo", "Yep");

    ItooPreferences data = new ItooPreferences(context);
    if (!data.contains("boot")) {
      return;
    }
    String boot = (String) data.read("boot", "");
    if (boot.equals("process")) {
      Intent service = new Intent(context, ItooService.class);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        context.startForegroundService(service);
      else context.startService(service);
    }
  }
}