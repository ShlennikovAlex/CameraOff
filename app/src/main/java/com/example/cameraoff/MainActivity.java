package com.example.cameraoff;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQ_CAMERA = 1;
    private static final int REQ_NOTIF = 2;

    private static final int ACCENT = 0xFFFFB300;
    private static final int PLAIN = 0xFFFFFFFF;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final View container = findViewById(R.id.container);

        // первые полсекунды чёрный экран, будто камера пытается запуститься
        handler.postDelayed(() -> container.animate().alpha(1f).setDuration(220).start(), 500);

        // кнопки, которые просто нажимаются и подсвечиваются
        final View.OnClickListener justPress = this::press;
        int[] simple = {
                R.id.btn_flash, R.id.btn_ev, R.id.btn_lens, R.id.btn_grid, R.id.btn_menu,
                R.id.btn_bokeh, R.id.btn_beauty, R.id.btn_thumb, R.id.btn_flip
        };
        for (int id : simple) {
            findViewById(id).setOnClickListener(justPress);
        }

        // спуск — моргает экраном
        findViewById(R.id.btn_shutter).setOnClickListener(v -> {
            press(v);
            blink();
        });

        // вкладки режимов
        final TextView tabVideo = findViewById(R.id.tab_video);
        final TextView tabPhoto = findViewById(R.id.tab_photo);
        final TextView tabPortrait = findViewById(R.id.tab_portrait);

        final View.OnClickListener tabClick = v -> {
            press(v);
            setTab(tabVideo, false);
            setTab(tabPhoto, false);
            setTab(tabPortrait, false);
            setTab((TextView) v, true);
        };
        tabVideo.setOnClickListener(tabClick);
        tabPhoto.setOnClickListener(tabClick);
        tabPortrait.setOnClickListener(tabClick);

        // зум
        final TextView z1 = findViewById(R.id.zoom_1x);
        final TextView z2 = findViewById(R.id.zoom_2x);

        final View.OnClickListener zoomClick = v -> {
            press(v);
            boolean first = (v == z1);
            setZoom(z1, first);
            setZoom(z2, !first);
        };
        z1.setOnClickListener(zoomClick);
        z2.setOnClickListener(zoomClick);

        askAndStartService();
    }

    private void press(View v) {
        v.animate().alpha(0.35f).setDuration(70).withEndAction(
                () -> v.animate().alpha(1f).setDuration(120).start()).start();
    }

    private void blink() {
        final View overlay = findViewById(R.id.flash_overlay);
        overlay.animate().alpha(0.45f).setDuration(60).withEndAction(
                () -> overlay.animate().alpha(0f).setDuration(180).start()).start();
    }

    private void setTab(TextView t, boolean active) {
        t.setTextColor(active ? ACCENT : PLAIN);
        t.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void setZoom(TextView t, boolean active) {
        t.setBackgroundResource(active ? R.drawable.bg_pill_active : 0);
        t.setTextColor(active ? ACCENT : PLAIN);
    }

    private void askAndStartService() {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startHolder();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code == REQ_CAMERA
                && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startHolder();
        }
    }

    private void startHolder() {
        Intent i = new Intent(this, CameraHoldService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception ignored) { }
    }
}
