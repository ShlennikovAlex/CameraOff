package com.example.cameraoff;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;

public class CameraHoldService extends Service {

    private static final String CHANNEL = "camera_hold2";

    private CameraManager manager;
    private CameraDevice device;
    private HandlerThread thread;
    private Handler handler;

    private String lastMessage = "";

    @Override
    public void onCreate() {
        super.onCreate();

        makeChannel();
        try {
            startForeground(1, buildNotification());
        } catch (Exception e) {
            report("ОШИБКА startForeground: " + e.getClass().getSimpleName());
        }

        thread = new HandlerThread("camera-hold");
        thread.start();
        handler = new Handler(thread.getLooper());

        report("служба запущена");
        open();
    }

    private void open() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            report("нет разрешения на камеру");
            return;
        }

        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            report("нет CameraManager");
            return;
        }

        try {
            final String id = manager.getCameraIdList()[0];
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    device = camera;
                    report("КАМЕРА ЗАХВАЧЕНА (id " + id + ")");
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    device = null;
                    report("камеру отобрали");
                    retry();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    device = null;
                    report("не смогла открыть, код " + error);
                    retry();
                }
            }, handler);
        } catch (CameraAccessException | SecurityException e) {
            report("не смогла открыть: " + e.getClass().getSimpleName());
            retry();
        }
    }

    private void retry() {
        if (handler != null) handler.postDelayed(this::open, 2500);
    }

    private void report(final String s) {
        if (s.equals(lastMessage)) return;
        lastMessage = s;
        new Handler(Looper.getMainLooper()).post(
                () -> Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show());
    }

    private void makeChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Camera", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setContentTitle("Camera")
                .setContentText("Camera is busy")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (device == null) open();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (device != null) device.close();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
