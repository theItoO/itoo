package xyz.kumaraswamy.itoo;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.util.Log;
import xyz.kumaraswamy.itoox.ItooCreator;

public class ItooJobService extends JobService {

    private static final String TAG = "ItooService";

    private ItooCreator creator;

    private boolean registered = false;
    private String[] actions;

    private String screen;
    private String procedure;

    JobParameters parms;
    private boolean restart;


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
        actions = extras.getStringArray("actions");
        if (actions == null || actions[0] == null) return true;

        IntentFilter filter = new IntentFilter(actions[0]);
        registerReceiver(receiver, filter);
        registered = true;
        return true;
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
