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

public class CameraHoldService extends Service {

    private static final String CHANNEL = "camera_hold";

    private CameraManager manager;
    private CameraDevice device;
    private HandlerThread thread;
    private Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();

        makeChannel();
        try {
            startForeground(1, buildNotification());
        } catch (Exception ignored) { }

        thread = new HandlerThread("camera-hold");
        thread.start();
        handler = new Handler(thread.getLooper());

        open();
    }

    private void open() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;

        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return;

        try {
            String id = manager.getCameraIdList()[0];
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    device = camera;
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    device = null;
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    device = null;
                    retry();
                }
            }, handler);
        } catch (CameraAccessException | SecurityException e) {
            retry();
        }
    }

    private void retry() {
        if (handler != null) handler.postDelayed(this::open, 2000);
    }

    private void makeChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Camera", NotificationManager.IMPORTANCE_MIN);
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
