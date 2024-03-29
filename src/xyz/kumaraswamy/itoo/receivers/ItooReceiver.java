// Copyright (C) 2023 Kumaraswamy B G
// GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007
// See LICENSE for full details
package xyz.kumaraswamy.itoo.receivers;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import xyz.kumaraswamy.itoo.Itoo;

public class ItooReceiver extends BroadcastReceiver {

    private static final String LOG_TAG = "ItooReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(LOG_TAG, "On Receive");
        Bundle extras = intent.getExtras();

        int jobId = extras.getInt("jobId");

        String screen = extras.getString("screen");
        String procedure = extras.getString("procedure");
        boolean restart = extras.getBoolean("restart");

//        String[] actions = extras.getStringArray("actions");

        JobInfo job = Itoo.build(context, jobId, 1000, restart, screen, procedure);
        JobScheduler scheduler = (JobScheduler)
                    context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

        boolean successful = scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
        Log.d(LOG_TAG, "Itoo Receiver, status = " + successful);
    }
}