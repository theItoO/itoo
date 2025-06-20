// Copyright (C) 2023 Kumaraswamy B G
// GNU GENERAL PUBLIC LICENSE Version 3, 29 June 2007
// See LICENSE for full details
package xyz.kumaraswamy.itoo;

import android.R;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.util.Log;

import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleEvent;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;

import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.EventDispatcher;
import com.google.appinventor.components.runtime.OnPauseListener;
import com.google.appinventor.components.runtime.OnResumeListener;
import com.google.appinventor.components.runtime.util.JsonUtil;
import com.google.appinventor.components.runtime.util.YailList;
import org.json.JSONException;

import xyz.kumaraswamy.itoo.receivers.BootReceiver;
import xyz.kumaraswamy.itoo.receivers.StartReceiver;
import xyz.kumaraswamy.itoo.scripts.ScriptManager;
import xyz.kumaraswamy.itoox.BackgroundProcedureReceiver;
import xyz.kumaraswamy.itoox.InstanceForm;
import xyz.kumaraswamy.itoox.ItooCreator;
import xyz.kumaraswamy.itoox.ItooInt;
import xyz.kumaraswamy.itoox.ItooPreferences;
import xyz.kumaraswamy.itoox.UIProcedureInvocation;
import xyz.kumaraswamy.itoox.capture.ComponentMapping;
import xyz.kumaraswamy.itoox.capture.PropertyCapture;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Itoo extends AndroidNonvisibleComponent implements OnPauseListener, OnResumeListener {

  private static final String TAG = "Itoo";

  private String screenName;

  private final JobScheduler scheduler;
  private final AlarmManager alarmManager;

  private String notificationIcon = "ic_dialog_alert";
  private String foregroundServiceType = "datasync";

  private final ItooPreferences data;
  private final ItooPreferences userData;
  private final ItooPreferences additionalConfig;

  private final Map<String, String> events = new HashMap<>();

  private final Map<String, BroadcastReceiver> registeredBroadcasts = new HashMap<>();

  private boolean isSky = false;
  private final ItooCreator creator;

  private boolean uiProcedureReceiverRegistered = false;

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
          if (procedure == null) {
            procedure = componentName + "_" + eventName;
          }
          formX.creator.startProcedureInvoke(procedure, args);
        }
      };

      formX.creator.addEndListener(new ItooCreator.EndListener() {
        @Override
        public void onEnd() {
          Log.i("Itoo", "onEnd() called");
          for (BroadcastReceiver register : registeredBroadcasts.values()) {
            form.unregisterReceiver(register);
          }
          registeredBroadcasts.clear();
        }
      });
    } else {
      form.registerForOnPause(this);
      form.registerForOnResume(this);

      creator = null;
      listenMessagesFromBackground();
    }
    data = new ItooPreferences(form);
    userData = new ItooPreferences(form, "stored_files");
    additionalConfig = new ItooPreferences(form, "AdditionalItooConfig");
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
    IntentFilter filter = new IntentFilter("itoo_x_reserved");
    filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // with flag "receiver isn't exported" for API levels 26 onwards
      form.registerReceiver(register, filter, 4);
    } else {
      form.registerReceiver(register, filter);
    }
    registeredBroadcasts.put("itoo_x_reserved", register);
  }

  @Override
  public void onResume() {
    Log.d(TAG, "onResume()");

    for (Map.Entry<String, BroadcastReceiver> broadcast : registeredBroadcasts.entrySet()) {
      IntentFilter filter = new IntentFilter(broadcast.getKey());
      filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // with flag "receiver isn't exported" for API levels 26 onwards
        form.registerReceiver(broadcast.getValue(), filter, 4);
      } else {
        form.registerReceiver(broadcast.getValue(), filter);
      }
    }
    UIProcedureInvocation.register();
    uiProcedureReceiverRegistered = true;
  }

  @SuppressWarnings("CommentedOutCode")
  @Override
  public void onPause() {
    Log.d(TAG, "onPause()");

    for (BroadcastReceiver register : registeredBroadcasts.values()) {
      form.unregisterReceiver(register);
    }
    if (uiProcedureReceiverRegistered) {
      try {
        UIProcedureInvocation.unregister();
      } catch (Throwable ignored) {}
      uiProcedureReceiverRegistered = false;
    }
    // EXPERIMENTAL: block forward
    // if (!isSky) {
    //  ActionReceiver.unregister(form);
    // }
  }

  @SimpleFunction
  public void RegisterEvent(String eventName, String procedure) {
    Log.d(TAG, "Registering event " + eventName + " procedure " + procedure);
    events.put(eventName, procedure);
  }

  @DesignerProperty(
          editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
          defaultValue = "True"
  )
  @SimpleProperty(description = "Produces extra logs that helps to debug faster. It is recommended to turn this feature" +
          "off in production mode to avoid leaking sensitive data in logcat.")
  public void Debug(boolean debug) throws JSONException {
    additionalConfig.write("debug_mode", debug);
  }

  @SimpleProperty
  public boolean InBackground() {
    return isSky;
  }

  @SimpleProperty
  public boolean Debug() {
    return (boolean) additionalConfig.read("debug_mode", true);
  }

  @SimpleProperty
  public void NotificationIcon(String icon) {
    notificationIcon = icon.trim();
  }

  @SimpleProperty
  public String NotificationIcon() {
    return notificationIcon;
  }

  @DesignerProperty(
          editorType = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
          editorArgs = {
                  "Camera",
                  "ConnectedDevice",
                  "DataSync",
                  "Health",
                  "Location",
                  "MediaPlayback",
                  "MediaProcessing",
                  "MediaProjection",
                  "Microphone",
                  "PhoneCall",
                  "RemoteMessaging",
                  "ShortService",
                  "SpecialUse",
                  "SystemExempted"
          },
          defaultValue = "DataSync"
  )
  @SimpleProperty(description = "Set a relevant foreground service type based on work done.")
  public void ForegroundServiceType(String type) {
    type = type.trim().toLowerCase(); // we have to sanitize
    foregroundServiceType = type;
  }

  @SimpleProperty
  public String ForegroundServiceType() {
    return foregroundServiceType;
  }

  @SimpleFunction
  public void SaveProcessForBoot(String procedure,
                                 String title,
                                 String subtitle) throws JSONException, ReflectiveOperationException {
    dumpDetails(procedure, title, subtitle, foregroundServiceType);
    data.write("boot", "process");
  }

  @SimpleFunction(description = "Starts a background service with procedure call")
  public boolean CreateProcess(String procedure,
                               String title,
                               String subtitle) throws Exception {
    Log.d(TAG, "CreateProcess");
    StopProcess();
    dumpDetails(procedure, title, subtitle, foregroundServiceType);

    Intent service = new Intent(form, ItooService.class);
    Log.d(TAG, "Service: " + service);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      form.startForegroundService(service);
    } else {
      form.startService(service);
    }
    Log.d(TAG, "Started");
    return true;
  }

  private void dumpDetails(String procedure,
                           String title,
                           String subtitle,
                           String serviceType) throws JSONException, ReflectiveOperationException {
    int notificationIconId = (int) R.drawable.class.getField(notificationIcon).get(null);
    data.write("screen", screenName)
            .write("procedure", procedure)
            .write("notification_title", title)
            .write("notification_subtitle", subtitle)
            .write("icon", String.valueOf(notificationIconId))
            .write("service_type", serviceType);
  }

  @SimpleFunction(description = "Call a background procedure while in the application.")
  public void CallBackgroundProcedure(String name, YailList args) throws JSONException {
    if (isSky) {
      // Only meant to be called from U.I
      return;
    }
    Object[] argObjects = args.toArray();
    int argsLen = argObjects.length;
    String[] serialized = new String[argsLen];
    for (int i = 0; i < argsLen; i++) {
      serialized[i] = JsonUtil.getJsonRepresentation(argObjects[i]);
    }
    Intent intent = new Intent(BackgroundProcedureReceiver.BACKGROUND_PROCEDURE_RECEIVER);
    intent.putExtra("procedure", name);
    intent.putExtra("args", serialized);
    intent.setPackage(form.getPackageName());
    form.sendBroadcast(intent);
  }

  @SimpleFunction
  public YailList CaptureProperties(Component component, YailList properties) throws JSONException, ReflectiveOperationException {
    List<String> listUnsuccessful = new PropertyCapture(form)
            .capture(form, ComponentMapping.getComponentName(component),
                    component,
                    properties.toStringArray());
    return YailList.makeList(listUnsuccessful);
  }

  @SimpleFunction
  public void ReleaseProperties(Component component) throws JSONException, InvocationTargetException, IllegalAccessException {
    // only called in the background
    new PropertyCapture(form).release(screenName,
            creator.getComponentName(component),
            component);
  }

  @SimpleFunction
  public void ExecuteInternalScript(int code, YailList values) {
    ScriptManager.handle(form, code, values.toArray());
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

    form.sendBroadcast(
            new Intent(ItooService.END_ACTION)
                    .putExtra("packageName", form.getPackageName())
    );
    form.stopService(new Intent(form, ItooService.class));
  }

  @SimpleFunction(description = "Check if a task corresponding to the Id is running")
  public boolean IsRunning(int jobId) {
    JobInfo info = scheduler.getPendingJob(jobId);
    // it is not null, because it is still
    // executing and pending
    return info != null;
  }

  // @SimpleFunction
  @SuppressWarnings("unused")
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

  @SimpleFunction(description = "Broadcasts a message to a service/process or the app")
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
    intent.setPackage(form.getPackageName());
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
    IntentFilter filter = new IntentFilter(name);
    filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY - 1);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // with flag "receiver isn't exported" for API levels 26 onwards
      form.registerReceiver(register, filter, 4);
    } else {
      form.registerReceiver(register, filter);
    }
    registeredBroadcasts.put(name, register);
  }

  @SimpleEvent(description = "Should be used for receiving events from the background process")
  public void BroadcastEvent(String name, Object message) {
    EventDispatcher.dispatchEvent(this, "BroadcastEvent", name, message);
  }

  @SimpleFunction
  public void UnregisterBroadcast(String name) {
    BroadcastReceiver register = registeredBroadcasts.get(name);
    if (register == null) {
      return;
    }
    form.unregisterReceiver(register);
    registeredBroadcasts.remove(name);
  }

  @SimpleFunction(description = "Cancels the service by Id")
  public void CancelTask(int jobId) {
    scheduler.cancel(jobId);
  }

  @SimpleFunction
  public void StoreProperty(String name, Object value) throws JSONException {
    userData.write(name, value);
  }

  @SimpleFunction
  public Object FetchProperty(String name, Object valueIfTagNotThere) {
    return userData.read(name, valueIfTagNotThere);
  }
}
