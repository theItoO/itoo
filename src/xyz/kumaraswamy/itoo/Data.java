package xyz.kumaraswamy.itoo;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class Data {

    private final Context context;

    public Data(Context context) {
        this.context = context;
    }

    public void put(String name, String value) throws IOException {
        File file = new File(context.getFilesDir(), name);

        FileOutputStream fos = new FileOutputStream(file);
        fos.write(value.getBytes());
        fos.close();
    }

    public String get(String name) throws IOException {
        File file = new File(context.getFilesDir(), name);

        FileInputStream fis = new FileInputStream(file);
        byte[] bytes = new byte[fis.available()];
        fis.read(bytes);
        return new String(bytes);
    }

    public void delete(String name) {
        File file = new File(context.getFilesDir(), name);
        file.delete();
    }

    public boolean exists(String name) {
        File file = new File(context.getFilesDir(), name);
        return file.exists();
    }
}