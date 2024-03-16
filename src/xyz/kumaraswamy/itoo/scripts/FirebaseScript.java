package xyz.kumaraswamy.itoo.scripts;

import com.google.appinventor.components.runtime.FirebaseDB;
import com.google.appinventor.components.runtime.Form;

public class FirebaseScript extends ScriptHandler {

  @Override
  public void handle(Form form, Object[] args) {
    // array[0] => component
    // array[1] => Firebase URL
    // array[2] => Firebase Token
    if (args.length != 3) {
      throw new IllegalArgumentException("Expected three values for Firebase Script");
    }
    if (!(args[0] instanceof FirebaseDB)) {
      throw new IllegalArgumentException("Expected firebase component at position 1");
    }
    if (!(args[1] instanceof String && args[2] instanceof String)) {
      throw new IllegalArgumentException("Expected URL and Token at position 2 and 3");
    }
    FirebaseDB firebase = (FirebaseDB) args[0];
    String url = (String) args[1];
    String token = (String) args[2];

    // set the default url first
    firebase.DefaultURL(url);
    firebase.FirebaseURL(url);

    firebase.FirebaseToken(token);
    firebase.Initialize();
  }
}
