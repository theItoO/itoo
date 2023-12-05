package xyz.kumaraswamy.itoo.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import xyz.kumaraswamy.itoox.ItooCreator;

public class StartReceiver extends BroadcastReceiver {

  private static final String TAG = "StartReceiver";

  @Override
  public void onReceive(Context context, Intent intent) {
    String procedure = intent.getStringExtra("procedure");
    String screenName = intent.getStringExtra("screen_name");

    Log.d(TAG, "onReceive(" + procedure + ", " + screenName + ")");
    try {
      new ItooCreator(
          context,
          procedure,
          screenName,
          true
      );
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
