package xyz.kumaraswamy.itoo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

   private static final String TAG = "AlarmXItoo";

   @Override
   public void onReceive(Context context, Intent intent) {
      Log.d(TAG, "onReceive() called");

      Intent service = new Intent(context, ItooService.class);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
         context.startForegroundService(service);
      else context.startService(service);
   }
}
