package xyz.kumaraswamy.itoo.scripts;

import com.google.appinventor.components.runtime.Form;

import java.util.HashMap;
import java.util.Map;

public class ScriptManager {
  public static final int SCRIPT_FIREBASE = 1;
  public static final int SCRIPT_CLOUD_DB = 2;

  private static Map<Integer, ScriptHandler> SCRIPTS = new HashMap<>();

  static {
    SCRIPTS.put(SCRIPT_FIREBASE, new FirebaseScript());
    SCRIPTS.put(SCRIPT_CLOUD_DB, new CloudDBScript());
  }

  public static void handle(Form form, int script, Object[] args) {
    ScriptHandler handler = SCRIPTS.get(script);
    if (handler == null) {
      throw new IllegalArgumentException("Script '" + script + "' not found");
    }
    handler.handle(form, args);
  }
}
