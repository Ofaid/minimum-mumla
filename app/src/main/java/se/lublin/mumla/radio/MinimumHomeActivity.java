/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import se.lublin.mumla.R;
import se.lublin.mumla.service.MumlaService;

/**
 * A deliberately small radio-device dashboard. Each page has one large, recoverable action.
 * Provisioning and boot can open it while OEM Launcher3 remains the Android HOME app.
 */
public final class MinimumHomeActivity extends Activity {
    private static final int PAGE_MINIMUM = 0;
    private static final int PAGE_SETTINGS = 1;

    private FrameLayout root;
    private TextView pageIndicator;
    private int page = PAGE_MINIMUM;
    private GestureDetector gestureDetector;
    private se.lublin.mumla.Settings settings;
    private ToneGenerator pttRecoveryTone;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(7, 25, 43));
        window.setNavigationBarColor(Color.rgb(7, 25, 43));
        settings = se.lublin.mumla.Settings.getInstance(this);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent start, MotionEvent end, float velocityX, float velocityY) {
                float distanceX = end.getX() - start.getX();
                if (Math.abs(distanceX) > Math.abs(end.getY() - start.getY())
                        && Math.abs(distanceX) > 24 && Math.abs(velocityX) > 50) {
                    showPage(distanceX < 0 ? page + 1 : page - 1);
                    return true;
                }
                return false;
            }
        });

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(7, 25, 43));
        setContentView(root);
        showPage(PAGE_MINIMUM);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (RadioPttKeyManager.isConfiguredPttKey(keyCode, settings)
                && !RadioPttKeyManager.isMediaStyleKey(keyCode)) {
            if (event.getRepeatCount() == 0) {
                playPttRecoveryAlert();
                launchRadioForPttRecovery();
            }
            return true;
        }
        if (isPreviousPageKey(keyCode)) {
            if (event.getRepeatCount() == 0) {
                showPage(page == PAGE_MINIMUM ? PAGE_SETTINGS : page - 1);
            }
            return true;
        }
        if (isNextPageKey(keyCode)) {
            if (event.getRepeatCount() == 0) {
                showPage(page == PAGE_SETTINGS ? PAGE_MINIMUM : page + 1);
            }
            return true;
        }
        if (isActivateKey(keyCode)) {
            if (event.getRepeatCount() == 0) {
                launchPageAction();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (RadioPttKeyManager.isConfiguredPttKey(keyCode, settings)
                && !RadioPttKeyManager.isMediaStyleKey(keyCode)) {
            signalPttReleased();
            return true;
        }
        if (isPreviousPageKey(keyCode) || isNextPageKey(keyCode)
                || isActivateKey(keyCode)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (pttRecoveryTone != null) {
            pttRecoveryTone.release();
            pttRecoveryTone = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (page != PAGE_MINIMUM) {
            showPage(PAGE_MINIMUM);
        }
    }

    private void showPage(int requestedPage) {
        page = Math.max(PAGE_MINIMUM, Math.min(PAGE_SETTINGS, requestedPage));
        root.removeAllViews();

        LinearLayout pageView = new LinearLayout(this);
        pageView.setOrientation(LinearLayout.VERTICAL);
        pageView.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        pageView.setPadding(dp(4), dp(4), dp(4), dp(2));
        pageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchPageAction();
            }
        });

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(getPageIcon());
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setContentDescription(getPageLabel());
        pageView.addView(icon, new LinearLayout.LayoutParams(-1, 0, 1.0f));

        TextView label = new TextView(this);
        label.setText(getPageLabel());
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setGravity(android.view.Gravity.CENTER);
        pageView.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));

        pageIndicator = new TextView(this);
        pageIndicator.setText((page + 1) + " / 2");
        pageIndicator.setTextColor(Color.rgb(180, 195, 205));
        pageIndicator.setTextSize(10);
        pageIndicator.setGravity(android.view.Gravity.CENTER);
        pageView.addView(pageIndicator, new LinearLayout.LayoutParams(-1, dp(18)));

        root.addView(pageView, new FrameLayout.LayoutParams(-1, -1));
    }

    private Drawable getPageIcon() {
        try {
            if (page == PAGE_MINIMUM) {
                ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(), 0);
                return info.loadIcon(getPackageManager());
            }
            if (page == PAGE_SETTINGS) {
                return getPackageManager().getApplicationIcon("com.android.settings");
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Fall through to a platform icon when an OEM removes an optional package.
        }
        return getDrawable(android.R.drawable.ic_menu_preferences);
    }

    private String getPageLabel() {
        if (page == PAGE_MINIMUM) {
            return getString(R.string.app_name);
        }
        if (page == PAGE_SETTINGS) {
            return "Settings";
        }
        return "Settings";
    }

    private void launchPageAction() {
        if (page == PAGE_MINIMUM) {
            startActivity(new Intent(this, RadioShellActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } else if (page == PAGE_SETTINGS) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (RuntimeException ignored) {
                showPage(PAGE_MINIMUM);
            }
        }
    }

    private void launchRadioForPttRecovery() {
        RadioPttRecoveryGuard.requireRelease();
        signalPttReleaseRequired();
        startActivity(new Intent(this, RadioShellActivity.class)
                .putExtra(RadioShellActivity.EXTRA_CONNECT_ON_PTT, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void signalPttReleaseRequired() {
        sendPttSafetyAction(MumlaService.ACTION_RADIO_REQUIRE_PTT_RELEASE);
    }

    private void signalPttReleased() {
        RadioPttRecoveryGuard.noteRelease();
        sendPttSafetyAction(MumlaService.ACTION_RADIO_PTT_RELEASED);
    }

    private void sendPttSafetyAction(String action) {
        try {
            startService(new Intent(this, MumlaService.class).setAction(action));
        } catch (RuntimeException ignored) {
            // RadioShell also applies the release lock once its service binding completes.
        }
    }

    private void playPttRecoveryAlert() {
        try {
            if (pttRecoveryTone == null) {
                pttRecoveryTone = new ToneGenerator(AudioManager.STREAM_MUSIC, 90);
            }
            pttRecoveryTone.startTone(ToneGenerator.TONE_SUP_ERROR, 700);
        } catch (RuntimeException ignored) {
            // The full-screen recovery state is still shown when an OEM audio path is unavailable.
        }
    }

    private boolean isPreviousPageKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
    }

    private boolean isNextPageKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    private boolean isActivateKey(int keyCode) {
        if (RadioDeviceProfile.T99.equals(RadioDeviceProfile.detectCurrent())) {
            return keyCode == KeyEvent.KEYCODE_MENU
                    || keyCode == KeyEvent.KEYCODE_DPAD_CENTER;
        }
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT
                || keyCode == KeyEvent.KEYCODE_CALL;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
