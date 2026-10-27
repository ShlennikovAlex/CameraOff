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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CameraHoldService extends Service {

    private static final String CHANNEL = "camera_hold2";

    private CameraManager manager;
    private final Map<String, CameraDevice> devices = new HashMap<>();
    private final Map<String, SurfaceTexture> textures = new HashMap<>();
    private final Map<String, Surface> surfaces = new HashMap<>();
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
        handler.post(this::openAll);
    }

    // захватываем ВСЕ камеры, какие есть
    private void openAll() {
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
            for (String id : manager.getCameraIdList()) {
                if (!devices.containsKey(id)) openOne(id);
            }
        } catch (CameraAccessException e) {
            report("не получила список камер");
            retry();
        }
    }

    private void openOne(final String id) {
        try {
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    devices.put(id, camera);
                    report("захвачена камера " + id);
                    startSession(id, camera);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    devices.remove(id);
                    report("отобрали камеру " + id);
                    retry();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    camera.close();
                    devices.remove(id);
                    report("ошибка камеры " + id + ", код " + error);
                    retry();
                }
            }, handler);
        } catch (CameraAccessException | SecurityException e) {
            report("не открылась камера " + id);
            retry();
        }
    }

    // держим камеру в работе, иначе телефон её отпускает
    private void startSession(final String id, final CameraDevice camera) {
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
                            try {
                                CaptureRequest.Builder b =
                                        camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                b.addTarget(surface);
                                session.setRepeatingRequest(b.build(), null, handler);
                                report("камера " + id + " в работе");
                            } catch (Exception e) {
                                report("запрос " + id + ": " + e.getClass().getSimpleName());
                            }
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

    private void retry() {
        if (handler != null) handler.postDelayed(this::openAll, 800);
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
        if (handler != null) handler.post(this::openAll);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        for (CameraDevice d : devices.values()) d.close();
        devices.clear();
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
