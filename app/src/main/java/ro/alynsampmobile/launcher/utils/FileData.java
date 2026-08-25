package ro.alynsampmobile.launcher.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import com.joom.paranoid.Obfuscate;

@Obfuscate
public class FileData {
    private final String name;
    private final String path;
    private final long size;
    private final String url;
    private final String gpu;

    public FileData(String name, String path, long size, String url, String gpu) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.url = url;
        this.gpu = gpu;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getUrl() {
        return url;
    }

    public String getGpu() {
        return gpu;
    }

    public static ArrayList<FileData> getListByJson(JSONObject json) throws JSONException {
        ArrayList<FileData> list = new ArrayList<>();
        if (!json.has("files")) return list;
        JSONArray arr = json.getJSONArray("files");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String name = obj.optString("name", "");
            String path = obj.optString("path", "");
            long size = 0;
            try {
                size = obj.optLong("size", 0);
                if (size == 0 && obj.has("size")) {
                    size = Long.parseLong(obj.getString("size"));
                }
            } catch (Exception ignored) {
            }
            String url = obj.optString("url", "");
            String gpu = obj.optString("gpu", "all");
            list.add(new FileData(name, path, size, url, gpu));
        }
        return list;
    }
}
