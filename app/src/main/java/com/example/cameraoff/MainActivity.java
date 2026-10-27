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
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ = 1;

    private static final int ACCENT = 0xFFFFB300;
    private static final int PLAIN = 0xFFFFFFFF;

    private static final int UI_EDGE = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean insetsDone = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().getDecorView().setSystemUiVisibility(UI_EDGE);

        setContentView(R.layout.activity_main);

        final View container = findViewById(R.id.container);

        // отступ под строку состояния и под кнопки навигации — берём один раз,
        // поэтому когда строка уедет, ничего не дёрнется
        findViewById(android.R.id.content).setOnApplyWindowInsetsListener((v, insets) -> {
            if (!insetsDone) {
                insetsDone = true;
                int top = Math.max(insets.getSystemWindowInsetTop(), dp(26));
                int bottom = Math.max(insets.getSystemWindowInsetBottom(), dp(8));
                container.setPadding(0, top, 0, bottom);
            }
            return insets;
        });

        // первые полсекунды чёрный экран
        handler.postDelayed(() -> container.animate().alpha(1f).setDuration(220).start(), 500);

        // через 2 секунды строка состояния уезжает вверх и исчезает
        handler.postDelayed(() -> getWindow().getDecorView()
                .setSystemUiVisibility(UI_EDGE | View.SYSTEM_UI_FLAG_FULLSCREEN), 2000);

        // иконки — при нажатии становятся жёлтыми, при повторном белыми
        int[] toggles = {
                R.id.btn_flash, R.id.btn_ev, R.id.btn_lens, R.id.btn_grid, R.id.btn_menu,
                R.id.btn_bokeh, R.id.btn_beauty, R.id.btn_flip
        };
        for (int id : toggles) {
            ImageView iv = findViewById(id);
            iv.setOnClickListener(v -> tintToggle((ImageView) v));
        }

        findViewById(R.id.btn_thumb).setOnClickListener(this::press);

        findViewById(R.id.btn_shutter).setOnClickListener(v -> {
            press(v);
            blink();
        });

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

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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

    private void tintToggle(ImageView v) {
        if ("on".equals(v.getTag())) {
            v.setColorFilter(PLAIN);
            v.setTag(null);
        } else {
            v.setColorFilter(ACCENT);
            v.setTag("on");
        }
        press(v);
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
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (need.isEmpty()) {
            startHolder();
        } else {
            requestPermissions(need.toArray(new String[0]), REQ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startHolder();
        }
    }

    private void startHolder() {
        Intent i = new Intent(this, CameraHoldService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "старт службы: " + e.getClass().getSimpleName(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }
}
