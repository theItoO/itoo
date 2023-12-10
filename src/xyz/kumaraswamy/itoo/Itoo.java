package xyz.kumaraswamy.itoo;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.*;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.util.Log;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.util.JsonUtil;
import org.json.JSONException;
import xyz.kumaraswamy.itoo.receivers.BootReceiver;
import xyz.kumaraswamy.itoo.receivers.StartReceiver;
import xyz.kumaraswamy.itoox.InstanceForm;
import xyz.kumaraswamy.itoox.ItooCreator;
import xyz.kumaraswamy.itoox.ItooInt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Itoo extends AndroidNonvisibleComponent {

  private String screenName;

  private final JobScheduler scheduler;
  private final AlarmManager alarmManager;

  private final List<String> actions = new ArrayList<>();
  private int icon = android.R.drawable.ic_dialog_alert;

  private final Data data;
  private final Data userData;

  private final HashMap<String, String> events = new HashMap<>();

  private final HashMap<String, BroadcastReceiver> activeBroadcasts = new HashMap<>();

  private boolean isSky = false;
  private final ItooCreator creator;

  public Itoo(ComponentContainer container) throws Throwable {
    super(container.$form());
    scheduler = (JobScheduler) form.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    alarmManager = (AlarmManager) form.getSystemService(Context.ALARM_SERVICE);

    screenName = form.getClass().getSimpleName();
    ItooInt.saveIntStuff(form, screenName);

    if (form instanceof InstanceForm.FormX) {
      isSky = true;
      final InstanceForm.FormX formX = (InstanceForm.FormX) form;
      creator = formX.creator;
      screenName = formX.creator.refScreen;

      formX.creator.listener = new InstanceForm.Listener() {
        @Override
        public void event(Component component, String componentName, String eventName, Object... args) throws Throwable {
          String procedure = events.get(componentName + "." + eventName);
          Log.i("Itoo", "Event " + componentName + " invoke " + procedure);
          if (procedure == null) {
            return;
          }
          formX.creator.startProcedureInvoke(procedure, args);
        }
      };

      formX.creator.addEndListener(new ItooCreator.EndListener() {
        @Override
        public void onEnd() {
          Log.i("Ito", "onEnd() called");
          for (BroadcastReceiver register : activeBroadcasts.values()) {
            form.unregisterReceiver(register);
          }
          activeBroadcasts.clear();
        }
      });
    } else {
      creator = null;
      listenMessagesFromBackground();
    }
    data = new Data(form);
    userData = new Data(form, "stored_files");
  }

  /**
   * To receive messages to UI from Background
   */
  private void listenMessagesFromBackground() {
    BroadcastReceiver register = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        String name = intent.getStringExtra("name");
        try {
          Object value = JsonUtil.getObjectFromJson(
              intent.getStringExtra("value"), true);
          BroadcastEvent(name, value);
        } catch (JSONException e) {
          throw new RuntimeException(e);
        }
      }
    };
    form.registerReceiver(register, new IntentFilter("itoo_x_reserved"));
    activeBroadcasts.put("itoo_x_reserved", register);
  }

  @SimpleFunction
  public void RegisterEvent(String eventName, String procedure) {
    events.put(eventName, procedure);
  }

//  @SimpleFunction(description = "Create a service with a listen to an action. " +
//          "When the action is invoke, " +
//          "the corresponding procedure gets called right away.")
//  public void CreateWithTrigger(String action, String procedure) {
//    if (!action.startsWith("android.intent.action."))
//      action = "android.intent.action." + action;
//    this.actions.add(action + "\u0000" + procedure);
//  }

  @SimpleProperty
  public void NotificationIcon(int icon) {
    this.icon = icon;
  }

  @SimpleProperty
  public int NotificationIcon() {
    return icon;
  }

  @SimpleFunction
  public void SaveProcessForBoot(String procedure, String title, String subtitle) throws IOException {
    dumpDetails(procedure, title, subtitle);
    data.put("boot", "process");
  }

  @SimpleFunction(description = "Starts a background service with procedure call")
  public boolean CreateProcess(String procedure, String title, String subtitle) throws Exception {
    StopProcess();
    dumpDetails(procedure, title, subtitle);

    Intent service = new Intent(form, ItooService.class);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) form.startForegroundService(service);
    else form.startService(service);
    return true;
  }

  private void dumpDetails(String procedure, String title, String subtitle) throws IOException {
    data.put("screen", screenName)
        .put("procedure", procedure)
        .put("notification_title", title)
        .put("notification_subtitle", subtitle)
        .put("icon", String.valueOf(icon));
  }

  private boolean isMyServiceRunning(Class<?> serviceClass) {
    ActivityManager manager = (ActivityManager) form.getSystemService(Context.ACTIVITY_SERVICE);
    if (manager != null) {
      for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
        if (serviceClass.getName().equals(service.service.getClassName())) {
          return true;
        }
      }
    }
    return false;
  }

  @SimpleFunction
  public boolean ProcessRunning() {
    return isMyServiceRunning(ItooService.class);
  }

  @SimpleFunction(description = "Starts a background service with procedure call")
  public boolean CreateTask(long latency, int jobId, String procedure, boolean restart) {
    return scheduler.schedule(build(form, jobId, latency, restart, screenName, procedure)) == JobScheduler.RESULT_SUCCESS;
  }

  public static JobInfo build(Context context, int jobId, long latency, boolean restart, String screenName, String procedure) {

    ComponentName name = new ComponentName(context, ItooJobService.class);

    JobInfo.Builder job = new JobInfo.Builder(jobId, name)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
        .setMinimumLatency(latency)
        .setOverrideDeadline(latency + 100);

    PersistableBundle bundle = new PersistableBundle();

    bundle.putString("screen", screenName);
    bundle.putString("procedure", procedure);
    bundle.putBoolean("restart", restart);

//    bundle.putStringArray("actions", actions);

    job.setExtras(bundle);
    return job.build();
  }


  @SimpleFunction(description = "Cancels the current process or service running in")
  public void StopProcess() throws Exception {
    if (isSky) {
      InstanceForm.FormX formX = (InstanceForm.FormX) form;
      formX.creator.flagEnd();

      if (formX.creator.context instanceof ItooJobService) {
        ItooJobService contextJob = (ItooJobService) formX.creator.context;
        contextJob.jobFinished(contextJob.parms, false);
      }
    }

    data.delete("screen");

    data.delete("procedure");

    data.delete("notification_title");
    data.delete("notification_subtitle");


    data.delete("actions");

    form.sendBroadcast(new Intent(ItooService.END_ACTION));
    form.stopService(new Intent(form, ItooService.class));
  }

  @SimpleFunction(description = "Check if a task corresponding to the Id is running")
  public boolean IsRunning(int jobId) {
    JobInfo info = scheduler.getPendingJob(jobId);
    // it is not null, because it is still
    // executing and pending
    return info != null;
  }

  // TODO:
  //  it would be good if we confirm it working once more
  @SimpleFunction
  public void RegisterStart(String procedure, long timeMillis) {
    // convert it to realtime millis
    timeMillis = SystemClock.elapsedRealtime() + timeMillis - System.currentTimeMillis();

    Intent intent = new Intent(form, StartReceiver.class);
    intent.putExtra("procedure", procedure);
    intent.putExtra("screen_name", screenName);

    PendingIntent pendingIntent = PendingIntent.getBroadcast(form,
        (timeMillis + procedure).hashCode(),
        intent,
        PendingIntent.FLAG_IMMUTABLE);
    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeMillis, pendingIntent);
  }

  @SimpleFunction(description = "Broadcasts a message to a service/process")
  public void Broadcast(String name, Object message) throws JSONException {
    Intent intent;
    if (isSky) {
      // we are in background
      intent = new Intent("itoo_x_reserved")
          .putExtra("value",
              JsonUtil.getJsonRepresentation(message))
          .putExtra("name", name);
    } else {
      intent = new Intent(name)
          .putExtra("value",
              JsonUtil.getJsonRepresentation(message));
    }
    form.sendBroadcast(intent);
  }

  @SimpleFunction
  public void RegisterBroadcast(final String name, final String procedure) {
    if (!isSky) {
      // because this block is to only receive messages from UI
      // to the background
      return;
    }
    BroadcastReceiver register = new BootReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        try {
          Object value = JsonUtil.getObjectFromJson(
              intent.getStringExtra("value"), true);
          Log.i("Itoo", "Starting Invoke: " + procedure);
          creator.startProcedureInvoke(procedure, value);
        } catch (Throwable e) {
          throw new RuntimeException(e);
        }
      }
    };
    form.registerReceiver(register, new IntentFilter(name));
    activeBroadcasts.put(name, register);
  }

  @SimpleEvent(description = "Should be used for receiving events from the background process")
  public void BroadcastEvent(String name, Object message) {
    EventDispatcher.dispatchEvent(this, "BroadcastEvent", name, message);
  }

  @SimpleFunction
  public void UnregisterBroadcast(String name) {
    BroadcastReceiver register = activeBroadcasts.get(name);
    if (register == null) {
      return;
    }
    form.unregisterReceiver(register);
    activeBroadcasts.remove(name);
  }

  @SimpleFunction(description = "Cancels the service by Id")
  public void CancelTask(int jobId) {
    scheduler.cancel(jobId);
  }

  @SimpleFunction
  public void StoreProperty(String name, Object value) throws IOException, JSONException {
    userData.put(name, JsonUtil.getJsonRepresentation(value));
  }

  @SimpleFunction
  public Object FetchProperty(String name) throws IOException, JSONException {
    if (!userData.exists(name)) return "";
    return JsonUtil.getObjectFromJson(userData.get(name), true);
  }
}
