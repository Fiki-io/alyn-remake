package ro.alynsampmobile.launcher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.downloader.Error;
import com.downloader.OnDownloadListener;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.joom.paranoid.Obfuscate;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import kotlin.jvm.internal.Ref;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ro.alynsampmobile.launcher.utils.FileData;
import ro.alynsampmobile.launcher.utils.Utils;

@Obfuscate
public class UpdateService extends Service {
    public Messenger mMessenger;
    public IncomingHandler mInHandler;
    public Messenger mActivityMessenger;

    public UpdateActivity.UpdateStatus mUpdateStatus = UpdateActivity.UpdateStatus.Undefined;
    public UpdateActivity.GameStatus mGameStatus = UpdateActivity.GameStatus.Undefined;

    public boolean mDownloadingStatus = false;
    public ArrayList<FileData> mUpdateFiles = new ArrayList<>();
    public long mUpdateFilesSizeTotal = 0;
    public int mDownloadFailedOffset;

    public String mUpdateVersion;

    public void onCreate() {
        HandlerThread thread = new HandlerThread("ServiceStartArguments", 10);
        thread.start();
        PRDownloader.initialize(getApplicationContext(), PRDownloaderConfig.newBuilder().setDatabaseEnabled(true).setReadTimeout(30000).setConnectTimeout(30000).build());
        mInHandler = new IncomingHandler(thread.getLooper());
        mMessenger = new Messenger(mInHandler);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    public IBinder onBind(Intent intent) {
        if (mMessenger != null) {
            return mMessenger.getBinder();
        }
        return null;
    }

    public boolean onUnbind(Intent intent) {
        return false;
    }

    public void onRebind(Intent intent) {
    }

    public void onDestroy() {
    }

    private final class IncomingHandler extends Handler {
        public IncomingHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            mActivityMessenger = msg.replyTo;
            // Log.i("UpdateService", "handleMessage -> " + msg.what);
            if (msg.what == 0) checkUpdate(); // check update
            else if (msg.what == 1) updateGameFiles(); // update files
            else if (msg.what == 2) updateGame(); // update game
            else if (msg.what == 4) { // get update status
                Message outMsg = Message.obtain(mInHandler, 4);
                outMsg.getData().putString(NotificationCompat.CATEGORY_STATUS, mUpdateStatus.name());
                outMsg.replyTo = mMessenger;
                if (mActivityMessenger != null) {
                    try {
                        mActivityMessenger.send(outMsg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            } else if (msg.what == 5) { // get game status
                Message outMsg = Message.obtain(mInHandler, 5);
                outMsg.getData().putString(NotificationCompat.CATEGORY_STATUS, mGameStatus.name());
                outMsg.replyTo = mMessenger;
                if (mActivityMessenger != null) {
                    try {
                        mActivityMessenger.send(outMsg);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            } else if (msg.what == 7) { // check update
                Log.i("UpdateService", "UPDATE_STATUS_GAME");
                checkUpdate();
            } else if (msg.what == 8) { // check update
                checkUpdate();
            }
        }
    }

    public void checkUpdate() {
        Log.d("UpdateService", "checkUpdate()");
        setUpdateStatus(UpdateActivity.UpdateStatus.CheckUpdate);

        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(Utils.update)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        assert response.body() != null;
                        String responseBody = response.body().string();
                        JSONObject data = new JSONObject(responseBody);

                        mUpdateVersion = data.getString("game_version");
                        Log.i("UpdateService", "mUpdateVersion = " + mUpdateVersion);

                        mUpdateFiles = new ArrayList<>();

                        mGameStatus = UpdateActivity.GameStatus.Undefined;
                        Message outMsg = Message.obtain(mInHandler, 10);
                        outMsg.replyTo = mMessenger;
                        if (mActivityMessenger != null) {
                            try {
                                mActivityMessenger.send(outMsg);
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }

                        String data_url = data.optString("game_files", "");
                        if (data_url.isEmpty()) {
                            data_url = getSharedPreferences("samp_settings", Context.MODE_PRIVATE).getString("files_type", "none").equals("full") ?
                                    data.optString("full_list_url", "https://alynsampmobile.pro/api/game-files") : data.optString("lite_list_url", "https://alynsampmobile.pro/api/game-files");
                        }
                        String samp_data_url = data.optString("samp_list_url", "");

                        checkGameFilesUpdate(data_url, samp_data_url);

                        if (isGameFilesUpdateExists()) {
                            mGameStatus = UpdateActivity.GameStatus.GameFilesUpdateRequired;
                        } else {
                            mGameStatus = UpdateActivity.GameStatus.Updated;
                        }

                        setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
                    } catch (Exception e) {
                        Log.e("UpdateService", Objects.requireNonNull(e.getMessage()));
                        mGameStatus = UpdateActivity.GameStatus.Undefined;
                        Message outMsg = Message.obtain(mInHandler, 5);
                        outMsg.getData().putString(NotificationCompat.CATEGORY_STATUS, mGameStatus.name());
                        outMsg.replyTo = mMessenger;
                        if (mActivityMessenger != null) {
                            try {
                                mActivityMessenger.send(outMsg);
                            } catch (RemoteException ee) {
                                ee.printStackTrace();
                            }
                        }
                    }
                }

                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    mGameStatus = UpdateActivity.GameStatus.Undefined;
                    Message outMsg = Message.obtain(mInHandler, 5);
                    outMsg.getData().putString(NotificationCompat.CATEGORY_STATUS, mGameStatus.name());
                    outMsg.replyTo = mMessenger;
                    if (mActivityMessenger != null) {
                        try {
                            mActivityMessenger.send(outMsg);
                        } catch (RemoteException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            });
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public boolean isGameUpdateExists() {
        PackageInfo packageInfo;
        Log.i("UpdateService", "isGameUpdateExists");
        PackageManager packageManager = getPackageManager();
        String currentVersion = null;
        if (packageManager != null) {
            try {
                packageInfo = packageManager.getPackageInfo(getPackageName(), PackageManager.GET_ACTIVITIES);
            } catch (PackageManager.NameNotFoundException e) {
                return true;
            }
        } else {
            packageInfo = null;
        }
        if (packageInfo != null) {
            currentVersion = packageInfo.versionName;
        }
        String sb = "isGameUpdateExists -> currentVersion " + currentVersion + " | mUpdateVersion " + mUpdateVersion;
        Log.d("UpdateService", sb);
        return (currentVersion == null || !currentVersion.equals(mUpdateVersion));
    }

    public boolean isGamePackageExists() {
        try {
            getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
        return true;
    }

    public void updateGame() {
        setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
        Message finishMsg = Message.obtain(mInHandler, 2);
        finishMsg.getData().putBoolean(NotificationCompat.CATEGORY_STATUS, true);
        finishMsg.replyTo = mMessenger;
        Messenger messenger = mActivityMessenger;
        if (messenger != null) {
            try {
                messenger.send(finishMsg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        setUpdateStatus(UpdateActivity.UpdateStatus.Undefined);
    }

    public void checkGameFilesUpdate(String list_url, String samp_list_url) throws Exception {
        Log.d("UpdateService", "checkGameFilesUpdate");

        mUpdateFilesSizeTotal = 0;

        String data_str = Utils.getStringOutputByURL(list_url);
        JSONObject data_json = new JSONObject(data_str);
        ArrayList<FileData> dataList = FileData.getListByJson(data_json);

        if (!samp_list_url.isEmpty()) {
            Log.d("UpdateService", "checkGameFilesUpdate -> samp_list_url");
            ArrayList<FileData> sampDataList = FileData.getListByJson(new JSONObject(Utils.getStringOutputByURL(samp_list_url)));
            dataList.addAll(sampDataList);
        } else {
            Log.d("UpdateService", "checkGameFilesUpdate -> samp_list_url is empty");
        }

        for (FileData fileData : dataList) {
            File forCheck = new File(getExternalFilesDir(null), fileData.getPath());

            boolean modifyFiles = getSharedPreferences("samp_settings", Context.MODE_PRIVATE).getBoolean("modify_files", false);

            // skip checking file size if not using modify_files
            if (modifyFiles ? forCheck.exists() : (forCheck.exists() && forCheck.length() == fileData.getSize())) {
                continue; // The file exists and has the correct size; no need to update.
            }

            if (!fileData.getGpu().equals("all")) {
                if ((fileData.getGpu().equals("dxt") && Utils.GPU_TYPE != Utils.GPUType.DXT) ||
                        (fileData.getGpu().equals("pvr") && Utils.GPU_TYPE != Utils.GPUType.PVR) ||
                        (fileData.getGpu().equals("etc") && Utils.GPU_TYPE != Utils.GPUType.ETC)) {
                    continue; // GPU type doesn't match; skip this file.
                }
            }

            System.out.println("Missing/Corrupted file: " + fileData.getPath() + " | " + fileData.getSize() + " bytes");
            System.out.println("File: " + forCheck.getAbsolutePath() + " | " + (forCheck.exists() ? forCheck.length() + " bytes" : "missing"));

            mUpdateFiles.add(fileData);
            mUpdateFilesSizeTotal += fileData.getSize();
        }
    }

    public void setUpdateStatus(UpdateActivity.UpdateStatus status) {
        if (!(status.name().isEmpty()) && mUpdateStatus != status) {
            mUpdateStatus = status;
            Message outMsg = Message.obtain(mInHandler, 4);
            outMsg.getData().putString(NotificationCompat.CATEGORY_STATUS, mUpdateStatus.name());
            outMsg.replyTo = mMessenger;
            Messenger messenger = mActivityMessenger;
            if (messenger != null) {
                try {
                    messenger.send(outMsg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void updateGameFiles() {
        if (isGameFilesUpdateExists()) {
            setUpdateStatus(UpdateActivity.UpdateStatus.DownloadGameFiles);
            downloadGameFiles();
            return;
        }
        Log.d("UpdateService", "updateGameFiles");
        Message outMsg = Message.obtain(mInHandler, 1);
        outMsg.getData().putBoolean(NotificationCompat.CATEGORY_STATUS, true);
        outMsg.replyTo = mMessenger;
        if (mActivityMessenger != null) {
            try {
                mActivityMessenger.send(outMsg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void downloadGameFiles() {
        Log.i("UpdateService", "Download Game Files (Parallel Multi-threaded Engine)");
        mDownloadFailedOffset = 0;
        final ArrayList<FileData> tempUpdateFiles = new ArrayList<>(mUpdateFiles);
        mUpdateFiles.clear();

        final AtomicLong currentBytes = new AtomicLong(0);
        final AtomicInteger completedFiles = new AtomicInteger(0);
        final AtomicLong lastUiUpdateTime = new AtomicLong(System.currentTimeMillis());
        final OkHttpClient downloadClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        sendLoadingScreen(false, "", 0, 0);

        // 4 concurrent parallel worker threads
        ExecutorService executor = Executors.newFixedThreadPool(4);

        for (int i = 0; i < tempUpdateFiles.size(); i++) {
            final FileData fileData = tempUpdateFiles.get(i);
            executor.submit(() -> {
                try {
                    File targetFile = new File(getExternalFilesDir(null), fileData.getPath());
                    File parentDir = targetFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // Check if file already exists with exact size (Resume / Skip support)
                    if (targetFile.exists() && targetFile.length() == fileData.getSize() && fileData.getSize() > 0) {
                        currentBytes.addAndGet(fileData.getSize());
                        completedFiles.incrementAndGet();
                        return;
                    }

                    File tempFile = new File(targetFile.getAbsolutePath() + ".download");
                    Request request = new Request.Builder().url(fileData.getUrl()).build();

                    boolean success = false;
                    for (int attempt = 0; attempt < 3 && !success; attempt++) {
                        try (Response response = downloadClient.newCall(request).execute()) {
                            if (response.isSuccessful() && response.body() != null) {
                                try (InputStream is = response.body().byteStream();
                                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                                    byte[] buffer = new byte[32768];
                                    int read;
                                    while ((read = is.read(buffer)) != -1) {
                                        fos.write(buffer, 0, read);
                                        long nowBytes = currentBytes.addAndGet(read);

                                        long nowTime = System.currentTimeMillis();
                                        if (nowTime - lastUiUpdateTime.get() > 100) {
                                            lastUiUpdateTime.set(nowTime);
                                            Message outMsg = Message.obtain(mInHandler, 4);
                                            outMsg.getData().putString(NotificationCompat.CATEGORY_STATUS, UpdateActivity.UpdateStatus.DownloadGameFiles.name());
                                            outMsg.getData().putBoolean("withProgress", true);
                                            outMsg.getData().putLong("current", nowBytes);
                                            outMsg.getData().putLong("total", mUpdateFilesSizeTotal);
                                            outMsg.getData().putString("filename", fileData.getName());
                                            outMsg.getData().putLong("totalfiles", (long) tempUpdateFiles.size());
                                            outMsg.getData().putLong("currentfile", (long) completedFiles.get());
                                            outMsg.replyTo = mMessenger;
                                            if (mActivityMessenger != null) {
                                                try {
                                                    mActivityMessenger.send(outMsg);
                                                } catch (RemoteException ignored) {
                                                }
                                            }
                                        }
                                    }
                                    fos.flush();
                                }

                                if (targetFile.exists()) {
                                    targetFile.delete();
                                }
                                tempFile.renameTo(targetFile);
                                success = true;
                            }
                        } catch (Exception e) {
                            if (attempt == 2) {
                                Log.e("UpdateService", "Failed to download " + fileData.getPath() + ": " + e.getMessage());
                            }
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }

                    completedFiles.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        new Thread(() -> {
            try {
                executor.awaitTermination(2, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            sendLoadingScreen(false, "", 0, 0);
            updateGame();
        }).start();
    }

    private void sendLoadingScreen(final boolean unpacking, final String fileName, final long current, final long total) {
        new Thread(() -> {
            Message outMsg = Message.obtain(mInHandler, 4);
            outMsg.getData().putString(NotificationCompat.CATEGORY_STATUS, UpdateActivity.UpdateStatus.CheckUpdate.name());
            outMsg.getData().putBoolean("withProgress", true);
            outMsg.getData().putString("filename", fileName);
            outMsg.getData().putBoolean("unpacking", unpacking);
            outMsg.getData().putLong("current", current);
            outMsg.getData().putLong("total", total);
            outMsg.replyTo = mMessenger;
            if (mActivityMessenger != null) {
                try {
                    mActivityMessenger.send(outMsg);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public boolean isGameFilesUpdateExists() {
        Log.i("UpdateService", "isGameFilesUpdateExists");
        return !mUpdateFiles.isEmpty();
    }
}
