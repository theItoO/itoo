package xyz.kumaraswamy.itoo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import xyz.kumaraswamy.itoox.ItooCreator;

import java.io.IOException;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("BootItoo", "Yep");

//        try {
//            new ItooCreator(context,
//                    "After_Reboot",
//                    "Screen1", true);
//        } catch (Throwable e) {
//            throw new RuntimeException(e);
//        }

        Data data = new Data(context);
        if (!data.exists("boot")) return;
        try {
            String boot = data.get("boot");
            if (boot.equals("process")) {
                Intent service = new Intent(context, ItooService.class);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(service);
                } else {
                    context.startService(service);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}