package xyz.kumaraswamy.itoo;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Data {

  private final File filesDir;

  public Data(Context context) {
    filesDir = context.getFilesDir();
  }

  public Data(Context context, String name) {
    filesDir = new File(context.getFilesDir(), "/" + name + "/");
    filesDir.mkdirs();
  }

  public Data put(String name, String value) throws IOException {
    File file = new File(filesDir, name);

    FileOutputStream fos = new FileOutputStream(file);
    fos.write(value.getBytes());
    fos.close();
    return this;
  }

  public String get(String name) throws IOException {
    File file = new File(filesDir, name);

    FileInputStream fis = new FileInputStream(file);
    byte[] bytes = new byte[fis.available()];
    fis.read(bytes);
    return new String(bytes);
  }

  public void delete(String name) {
    File file = new File(filesDir, name);
    if (file.exists())
      file.delete();
  }

  public boolean exists(String name) {
    return new File(filesDir, name).exists();
  }
}