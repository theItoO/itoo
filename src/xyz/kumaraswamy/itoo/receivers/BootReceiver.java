package xyz.kumaraswamy.itoo.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import xyz.kumaraswamy.itoo.Data;
import xyz.kumaraswamy.itoo.ItooService;

import java.io.IOException;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d("BootItoo", "Yep");

    Data data = new Data(context);
    if (!data.exists("boot")) return;
    try {
      String boot = data.get("boot");
      if (boot.equals("process")) {
        Intent service = new Intent(context, ItooService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
          context.startForegroundService(service);
        else context.startService(service);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}