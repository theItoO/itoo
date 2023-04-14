package xyz.kumaraswamy.itoo;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import org.json.JSONException;
import xyz.kumaraswamy.itoox.InstanceForm;
import xyz.kumaraswamy.itoox.ItooInt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Itoo extends AndroidNonvisibleComponent {

  private String screenName;
  private final JobScheduler scheduler;
  private final AlarmManager manager;

//  private final List<String> actions = new ArrayList<>();
  private int icon = android.R.drawable.ic_dialog_alert;

  private final Data data;
  private final Data userData;

  private final HashMap<String, String> events = new HashMap<>();

  public Itoo(ComponentContainer container) throws Throwable {
    super(container.$form());
    scheduler = (JobScheduler) form.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    manager = (AlarmManager) form.getSystemService(Context.ALARM_SERVICE);

    screenName = form.getClass().getSimpleName();
    ItooInt.saveIntStuff(form, screenName);
    if (form instanceof InstanceForm.FormX) {
      screenName = ((InstanceForm.FormX) form).creator.refScreen;
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
    userData = new Data(form, "stored_files");
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
  public void SaveProcessForBoot(String procedure,
                                 String title,
                                 String subtitle) throws IOException, JSONException {
    dumpDetails(procedure, title, subtitle);
    data.put("boot", "process");
  }

  @SimpleFunction(description = "Starts a background service with procedure call")
  public boolean CreateProcess(String procedure, String title, String subtitle) throws IOException {
    StopProcess();
    dumpDetails(procedure, title, subtitle);

    Intent service = new Intent(form, ItooService.class);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      form.startForegroundService(service);
    else form.startService(service);
    return true;
  }

  @SimpleFunction(description = "Sets an alarm at the given time. The procedure will be invoked when the alarm is fired.")
  public void CreateAlarm(String procedure, String title, String subtitle, long time) throws IOException {
    dumpDetails(procedure, title, subtitle);

    Intent intent = new Intent(form, AlarmReceiver.class);
    PendingIntent pd = PendingIntent.getBroadcast(form, 7, intent, PendingIntent.FLAG_IMMUTABLE);
    AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(time, pd);

    manager.setAlarmClock(info, pd);
  }

  @SimpleFunction(description = "To be called from the background service.")
  public void LaunchApp(String screen) throws JSONException, ClassNotFoundException {
    if (!(form instanceof InstanceForm.FormX))
      return;
    InstanceForm.FormX formX = (InstanceForm.FormX) form;
    ItooInt itooInt = new ItooInt(form, formX.creator.refScreen);

    String pkgName = itooInt.getScreenPkgName(screen);
    Log.d("ItooCasual", "Starting activity " + pkgName);
    Intent intent = new Intent(formX.creator.context, Class.forName(pkgName));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

    formX.creator.context.startActivity(intent);
  }

  private void dumpDetails(String procedure,
                           String title,
                           String subtitle) throws IOException {
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

//    return scheduler.schedule(build(
//            form, jobId, latency, restart, screenName, procedure,
//            new String[]{JsonUtil.getJsonRepresentation(actions)}))
//            == JobScheduler.RESULT_SUCCESS;

    return scheduler.schedule(build(
            form, jobId, latency, restart, screenName, procedure))
            == JobScheduler.RESULT_SUCCESS;
  }

  public static JobInfo build(Context context,
                              int jobId,
                              long latency,
                              boolean restart,
                              String screenName,
                              String procedure) {

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

  @SimpleFunction
  public void StoreProperty(String name, String value) throws IOException {
    userData.put(name, value);
  }

  @SimpleFunction
  public String FetchProperty(String name) throws IOException {
    if (!userData.exists(name))
      return "";
    return userData.get(name);
  }
}
