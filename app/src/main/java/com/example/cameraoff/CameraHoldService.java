package com.example.cameraoff;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.view.Surface;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CameraHoldService extends Service {

    private static final String CHANNEL = "camera_hold3";

    private CameraManager manager;
    private final Map<String, CameraDevice> devices = new HashMap<>();
    private final Map<String, SurfaceTexture> textures = new HashMap<>();
    private final Map<String, Surface> surfaces = new HashMap<>();
    private final Set<String> withSession = new HashSet<>();
    private final Set<String> pending = new HashSet<>();

    private HandlerThread thread;
    private Handler handler;

    private String lastMessage = "";

    // сторож: каждые 400 мс проверяем, что все камеры по-прежнему наши
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            openAll();
            if (handler != null) handler.postDelayed(this, 400);
        }
    };

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
        handler.post(watchdog);
    }

    private void openAll() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;

        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) return;

        try {
            for (String id : manager.getCameraIdList()) {
                if (!devices.containsKey(id) && !pending.contains(id)) {
                    pending.add(id);
                    openOne(id);
                }
            }
        } catch (CameraAccessException ignored) { }

        for (String id : new ArrayList<>(devices.keySet())) {
            if (!withSession.contains(id)) startSession(id, devices.get(id));
        }
    }

    private void openOne(final String id) {
        try {
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    pending.remove(id);
                    devices.put(id, camera);
                    report("захвачена камера " + id);
                    startSession(id, camera);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    pending.remove(id);
                    devices.remove(id);
                    withSession.remove(id);
                    report("отобрали камеру " + id);
                    handler.postDelayed(CameraHoldService.this::openAll, 150);
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    pending.remove(id);
                    devices.remove(id);
                    withSession.remove(id);
                    report("ошибка камеры " + id + ", код " + error);
                    handler.postDelayed(CameraHoldService.this::openAll, 150);
                }
            }, handler);
        } catch (CameraAccessException | SecurityException e) {
            pending.remove(id);
            report("не открылась камера " + id);
        }
    }

    private void startSession(final String id, final CameraDevice camera) {
        SurfaceTexture old = textures.get(id);
        if (old != null) {
            textures.remove(id);
            surfaces.remove(id);
            try { old.release(); } catch (Exception ignored) { }
        }

        try {
            SurfaceTexture st = new SurfaceTexture(0);
            st.setDefaultBufferSize(640, 480);
            final Surface surface = new Surface(st);
            textures.put(id, st);
            surfaces.put(id, surface);

            camera.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            withSession.add(id);
                            try {
                                CaptureRequest.Builder b =
                                        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                b.addTarget(surface);
                                session.setRepeatingRequest(b.build(), null, handler);
                                report("камера " + id + " в работе");
                            } catch (Exception ignored) { }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            report("сессия " + id + " не настроилась");
                        }
                    }, handler);
        } catch (Exception e) {
            report("сессия " + id + ": " + e.getClass().getSimpleName());
        }
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
        if (handler != null) handler.post(watchdog);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        for (CameraDevice d : devices.values()) d.close();
        devices.clear();
        withSession.clear();
        pending.clear();
        textures.clear();
        surfaces.clear();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
