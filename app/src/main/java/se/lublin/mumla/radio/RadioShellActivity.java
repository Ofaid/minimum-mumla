/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import se.lublin.mumla.R;
import se.lublin.mumla.service.IMumlaService;
import se.lublin.mumla.service.MumlaService;

/** Minimal radio-facing shell. It is deliberately not the launcher until the radio flavor is ready. */
public final class RadioShellActivity extends AppCompatActivity {
    private IMumlaService service;
    private Button pttButton;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((MumlaService.MumlaBinder) binder).getService();
            updatePttState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            updatePttState();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView identity = new TextView(this);
        identity.setText(getString(R.string.app_name) + "\n"
                + "Device: " + new DeviceIdentityManager(
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
                .getOrCreateDeviceId() + "\n"
                + "Profile: " + RadioDeviceProfile.detectCurrent());
        identity.setTextSize(18);
        root.addView(identity, new LinearLayout.LayoutParams(-1, -2));

        pttButton = new Button(this);
        pttButton.setText("PTT");
        pttButton.setTextSize(28);
        pttButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (service == null) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    service.onTalkKeyDown();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    service.onTalkKeyUp();
                    return true;
                }
                return true;
            }
        });
        root.addView(pttButton, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        bindService(new android.content.Intent(this, MumlaService.class), serviceConnection, 0);
        updatePttState();
    }

    @Override
    protected void onPause() {
        if (service != null) {
            service.onTalkKeyUp();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (service != null) {
            service.onTalkKeyUp();
        }
        unbindService(serviceConnection);
        super.onDestroy();
    }

    private void updatePttState() {
        if (pttButton != null) {
            pttButton.setEnabled(service != null);
        }
    }
}
