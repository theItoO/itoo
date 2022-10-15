package xyz.kumaraswamy.itoo;

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
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import xyz.kumaraswamy.itoox.InstanceForm;
import xyz.kumaraswamy.itoox.ItooInt;

import java.io.File;
import java.util.HashMap;
import java.util.Random;

public class Itoo extends AndroidNonvisibleComponent {

  private final String screenName;
  private final JobScheduler scheduler;

  private final NotificationManager manager;

  private String action, procedure;

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
              formX.creator.startProcedureInvoke(procedure, args);
          }
        };
      }
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
    this.action = action;
    this.procedure = procedure;
  }

  @SimpleFunction(description = "Starts a background service with procedure call")
  public boolean CreateProcess(String procedure, String title, String subtitle) {
    form.startService(new Intent(form, ItooService.class) {{
        putExtra("screen", screenName);
        putExtra("procedure", procedure);

        putExtra("notification_title", title);
        putExtra("notification_subtitle", subtitle);

        putExtra("actions", new String[] {action, Itoo.this.procedure});
    }});
    return true;
  }

  @SimpleFunction(description = "Ends the service from inside")
  public void StopProcess() {
      form.stopService(new Intent(form, ItooService.class));
  }

  @SimpleFunction(description = "Starts a background service with procedure call")
  public boolean CreateTask(long latency,
                            int jobId,
                            String procedure,
                            boolean restart) {

      return scheduler.schedule(build(
              form, jobId, latency, restart, screenName, procedure,
              new String[] {    this.action, this.procedure     }))
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
  public void Notification(int id, String title, String text, String sound) {
    createNotificationChannel(sound);
    Notification.Builder builder = new Notification.Builder(form, "Itoo")
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                    PendingIntent.getBroadcast(form,
                      500, new Intent(),
                      PendingIntent.FLAG_IMMUTABLE))
            .setSmallIcon(android.R.drawable.ic_dialog_info);
    manager.notify(id, builder.build());
  }

  private void createNotificationChannel(String sound) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationChannel serviceChannel = new NotificationChannel(
              "Itoo",
              "Itoo",
              NotificationManager.IMPORTANCE_MAX
      );
      if (!sound.isEmpty()) {
          AudioAttributes audioAttributes = new AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .setUsage(AudioAttributes.USAGE_ALARM)
          .build();

          serviceChannel.setSound(Uri.fromFile(new File(sound)), audioAttributes);
      }
      manager.createNotificationChannel(serviceChannel);
    }
  }
}
