package xyz.kumaraswamy.itoo.scripts;

import com.google.appinventor.components.runtime.CloudDB;
import com.google.appinventor.components.runtime.Form;

public class CloudDBScript extends ScriptHandler {
  @Override
  public void handle(Form form, Object[] args) {
    // array[0] => component
    // array[1] => Project Id
    // array[2] => Redis Port
    // array[3] => Redis Server
    // array[4] => Redis Token
    // array[5] => Use SSL
    if (args.length != 6) {
      throw new IllegalArgumentException("Expected six values for CloudDB Script");
    }
    if (!(args[0] instanceof CloudDB)) {
      throw new IllegalArgumentException("Expected Cloud DB component at position 1");
    }
    if (!(args[1] instanceof String && args[3] instanceof String && args[4] instanceof String)) {
      throw new IllegalArgumentException("Expected URL and Token at position 1, 3 and 4");
    }
    CloudDB database = (CloudDB) args[0];

    String projectId = (String) args[1];
    int port = Integer.parseInt(args[2].toString());

    String server = (String) args[3];
    String token = (String) args[4];

    boolean useSsl = (boolean) args[5];

    // begin initialisation
    database.ProjectID(projectId);
    database.RedisPort(port);

    database.UseSSL(useSsl);
    database.Token(token);

    database.DefaultRedisServer(server);
    database.RedisServer(server);

    database.Initialize();
  }
}
