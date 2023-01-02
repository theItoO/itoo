package xyz.kumaraswamy.itoo;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.JsonUtil;
import org.json.JSONException;
import xyz.kumaraswamy.itoox.InstanceForm;
import xyz.kumaraswamy.itoox.ItooInt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Itoo extends AndroidNonvisibleComponent {

  private final String screenName;
  private final JobScheduler scheduler;

  private final NotificationManager manager;

  private final List<String> actions = new ArrayList<>();
  private int icon = android.R.drawable.ic_dialog_alert;

  private final Data data;
  private final HashMap<String, String> events = new HashMap<>();

  public Itoo(ComponentContainer container) throws Throwable {
    super(container.$form());
    scheduler = (JobScheduler) form.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    manager = (NotificationManager) form.getSystemService(Context.NOTIFICATION_SERVICE);
    screenName = form.getClass().getSimpleName();
    ItooInt.saveIntStuff(form, screenName);
    if (form instanceof InstanceForm.FormX) {
      InstanceForm.FormX formX = (InstanceForm.FormX) form;
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
    }
    data = new Data(form);
  }

  @SimpleFunction
  public void RegisterEvent(String eventName, String procedure) {
    events.put(eventName, procedure);
  }

  @SimpleFunction(description = "Create a service with a listen to an action. " +
          "When the action is invoke, " +
          "the corresponding procedure gets called right away.")
  public void CreateWithTrigger(String action, String procedure) {
    if (!action.startsWith("android.intent.action.")) {
      action = "android.intent.action." + action;
    }
    this.actions.add(action + "\u0000" + procedure);
  }

  @SimpleProperty
  public void NotificationIcon(int icon) {
    this.icon = icon;
  }

  @SimpleProperty
  public int NotificationIcon() {
    return icon;
  }

  @SimpleFunction
  public void SaveProcessForBoot() throws IOException {
    data.put("boot", "process");
  }

  @SimpleFunction(description = "Starts a background service with procedure call")
  public boolean CreateProcess(String procedure, String title, String subtitle) throws IOException, JSONException {
    StopProcess();
    data.put("screen", screenName);

    data.put("procedure", procedure);

    data.put("notification_title", title);
    data.put("notification_subtitle", subtitle);
    data.put("icon", String.valueOf(icon));

    data.put("actions", JsonUtil.getJsonRepresentation(actions));
    Intent service = new Intent(form, ItooService.class);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      form.startForegroundService(service);
    } else {
      form.startService(service);
    }
    return true;
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

  @SimpleFunction(description = "Ends the service from inside")
  public void StopProcess() {
    data.delete("screen");

    data.delete("procedure");

    data.delete("notification_title");
    data.delete("notification_subtitle");


    data.delete("actions");

    form.stopService(new Intent(form, ItooService.class));
  }

  @SimpleFunction(description = "Starts a background service with procedure call")
  public boolean CreateTask(long latency,
                            int jobId,
                            String procedure,
                            boolean restart) throws JSONException {

    return scheduler.schedule(build(
            form, jobId, latency, restart, screenName, procedure,
            new String[]{JsonUtil.getJsonRepresentation(actions)}))
            == JobScheduler.RESULT_SUCCESS;
  }

  public static JobInfo build(Context context,
                              int jobId,
                              long latency,
                              boolean restart,
                              String screenName,
                              String procedure,
                              String[] actions) {

    ComponentName name = new ComponentName(context, ItooJobService.class);

    JobInfo.Builder job = new JobInfo.Builder(jobId, name)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .setMinimumLatency(latency)
            .setOverrideDeadline(latency + 100);

    PersistableBundle bundle = new PersistableBundle();

    bundle.putString("screen", screenName);
    bundle.putString("procedure", procedure);
    bundle.putBoolean("restart", restart);

    bundle.putStringArray("actions", actions);

    job.setExtras(bundle);
    return job.build();
  }

  @SimpleFunction(description = "Ends the service from inside the service only.")
  public void StopTask() throws Exception {
    if (form instanceof InstanceForm.FormX) {
      InstanceForm.FormX formX = (InstanceForm.FormX) form;
      formX.creator.flagEnd();
      ItooJobService context = (ItooJobService) formX.creator.context;
      context.jobFinished(context.parms, false);
    } else {
      throw new YailRuntimeError("Use CancelTask block instead when calling outside the service", "Itoo");
    }
  }

  @SimpleFunction(description = "Check if a task corresponding to the Id is running")
  public boolean IsRunning(int jobId) {
    JobInfo info = scheduler.getPendingJob(jobId);
    // it is not null, because it is still
    // executing and pending
    return info != null;
  }

  @SimpleFunction(description = "Cancels the service by Id")
  public void CancelTask(int jobId) {
    scheduler.cancel(jobId);
  }

  @SimpleFunction(description = "Sends a simple notification, sound can be empty.")
  public void Notification(int id,
                           String title,
                           String text,
                           String subtext,
                           boolean hasSound,
                           String bigtext) {
    createNotificationChannel(hasSound);
    Notification.Builder builder;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      builder = new Notification.Builder(form, "Itoo");
    } else {
      builder = new Notification.Builder(form);
    }
    builder.setContentTitle(title)
            .setContentText(text)
            .setSubText(subtext)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                    PendingIntent.getBroadcast(form,
                            500, new Intent(),
                            PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(android.R.drawable.ic_dialog_info);
    if (!bigtext.isEmpty()) {
      builder.setStyle(new Notification.BigTextStyle().bigText(bigtext));
    }
    manager.notify(id, builder.build());
  }

  private void createNotificationChannel(boolean sound) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel serviceChannel = new NotificationChannel(
              "Itoo",
              "Itoo",
              NotificationManager.IMPORTANCE_MAX
      );
      if (sound) {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();

        serviceChannel.setSound(Uri.fromFile(new File("")), audioAttributes);
      }
      manager.createNotificationChannel(serviceChannel);
    }
  }
}
