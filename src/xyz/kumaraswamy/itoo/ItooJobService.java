// Copyright (C) 2023 Kumaraswamy B G
// GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007
// See LICENSE for full details
package xyz.kumaraswamy.itoo;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.util.Log;
import xyz.kumaraswamy.itoo.receivers.ItooReceiver;
import xyz.kumaraswamy.itoox.ItooCreator;

import java.util.HashMap;

public class ItooJobService extends JobService {

    private static final String TAG = "ItooService";

    private ItooCreator creator;

    private final HashMap<String, String> actions = new HashMap<>();

    private String screen;
    private String procedure;

    JobParameters parms;
    private boolean restart;

    private boolean registered = false;


    @Override
    public boolean onStartJob(JobParameters parms) {
        Log.d(TAG, "Job Started[]");
        this.parms = parms;

        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Doraemon");

        if (wakeLock.isHeld()) wakeLock.release();
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(86_400_000L);

        PersistableBundle extras = parms.getExtras();

        screen = extras.getString("screen");
        procedure = extras.getString("procedure");
        restart = extras.getBoolean("restart");

        try {
            creator = new ItooCreator(
                    parms.getJobId(),
                    this,
                    procedure,
                    screen, true);
        } catch (Throwable e) {
            Log.e(TAG, "Error While Executing Procedure");
            throw new RuntimeException(e);
        }
      return true;
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

    @Override
    public boolean onStopJob(JobParameters parms) {
        Log.d(TAG, "Job Ended []");
        try {
            creator.flagEnd();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (registered) {
            unregisterReceiver(receiver);
        }
        if (restart) {
            Intent rIntent = new Intent(this, ItooReceiver.class);
            rIntent.putExtra("jobId", parms.getJobId());

            rIntent.putExtra("screen", screen);
            rIntent.putExtra("restart", restart);

            rIntent.putExtra("procedure", procedure);
            rIntent.putExtra("actions", actions);

            sendBroadcast(rIntent);
        }
        return false;
    }
}
