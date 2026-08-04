/*
 * Copyright (C) 2026 The Mumla contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package se.lublin.mumla.radio;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

import se.lublin.humla.HumlaService;
import se.lublin.humla.IHumlaSession;
import se.lublin.humla.model.IChannel;
import se.lublin.humla.model.IUser;
import se.lublin.humla.model.Server;
import se.lublin.humla.model.TalkState;
import se.lublin.humla.util.HumlaException;
import se.lublin.humla.util.HumlaObserver;
import se.lublin.mumla.R;
import se.lublin.mumla.Settings;
import se.lublin.mumla.app.ServerConnectTask;
import se.lublin.mumla.db.MumlaDatabase;
import se.lublin.mumla.db.MumlaSQLiteDatabase;
import se.lublin.mumla.service.IMumlaService;
import se.lublin.mumla.service.MumlaService;
import se.lublin.mumla.util.MumlaTrustStore;

/** Single-screen radio client driven by the Last Known Good Minimum config. */
public final class RadioShellActivity extends AppCompatActivity {
    private static final int MICROPHONE_PERMISSION_REQUEST = 73;
    private static final int COLOR_READY = Color.rgb(0, 108, 78);
    private static final int COLOR_BUSY = Color.rgb(173, 104, 0);
    private static final int COLOR_TX = Color.rgb(176, 24, 38);
    private static final int COLOR_OFFLINE = Color.rgb(70, 75, 82);
    private static final int COLOR_ERROR = Color.rgb(145, 25, 35);

    private final Map<Integer, String> activeTalkers = new LinkedHashMap<>();

    private IMumlaService service;
    private MumlaDatabase database;
    private Settings settings;
    private RadioConnectionConfig config;
    private int selectedRoomIndex;
    private boolean bound;
    private boolean destroyed;
    private boolean connectRequested;
    private boolean reconnectAfterDisconnect;
    private boolean retryAfterConfiguredCertificateTrust;
    private boolean configReceiverRegistered;
    private boolean pendingConfigTrial;
    private boolean pendingConfigIoInFlight;

    private TextView serviceNameView;
    private TextView statusView;
    private TextView roomView;
    private TextView identityView;
    private Button pttButton;

    private final BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (RadioConfigUpdater.ACTION_CONFIG_PENDING.equals(intent.getAction())) {
                maybeApplyPendingConfiguration();
            }
        }
    };

    private final HumlaObserver observer = new HumlaObserver() {
        @Override
        public void onConnecting() {
            setStatus(COLOR_BUSY, getString(R.string.radio_connecting));
        }

        @Override
        public void onConnected() {
            connectRequested = false;
            activeTalkers.clear();
            joinSelectedRoom();
            updateFromService();
            if (!pendingConfigTrial) {
                maybeApplyPendingConfiguration();
            }
        }

        @Override
        public void onDisconnected(HumlaException error) {
            connectRequested = false;
            activeTalkers.clear();
            pttButton.setEnabled(false);
            if (retryAfterConfiguredCertificateTrust) {
                retryAfterConfiguredCertificateTrust = false;
                setStatus(COLOR_BUSY, getString(R.string.radio_certificate_trusted));
                statusView.postDelayed(RadioShellActivity.this::maybeConnect, 400);
                return;
            }
            if (pendingConfigTrial && shouldRejectPendingTrial(error)) {
                failPendingConfiguration();
                return;
            }
            if (reconnectAfterDisconnect) {
                reconnectAfterDisconnect = false;
                maybeConnect();
                return;
            }
            if (service != null && service.isReconnecting()) {
                setStatus(COLOR_BUSY, getString(R.string.radio_reconnecting));
            } else {
                setStatus(COLOR_OFFLINE, getString(R.string.radio_offline));
            }
            maybeApplyPendingConfiguration();
        }

        @Override
        public void onTLSHandshakeFailed(X509Certificate[] chain) {
            trustConfiguredCertificate(chain);
        }

        @Override
        public void onUserJoinedChannel(IUser user, IChannel newChannel, IChannel oldChannel) {
            if (isSelf(user)) {
                roomView.setText(RoomPathResolver.fullPath(newChannel));
                showReadyState();
                if (pendingConfigTrial && isSelectedRoom(newChannel)) {
                    commitPendingConfiguration();
                }
            }
        }

        @Override
        public void onUserTalkStateUpdated(IUser user) {
            if (service == null || user == null) {
                return;
            }
            if (isSelf(user)) {
                if (isAudibleTalkState(user.getTalkState())) {
                    setStatus(COLOR_TX, getString(R.string.radio_transmitting));
                } else {
                    showTrafficOrReady();
                    maybeApplyPendingConfiguration();
                }
                return;
            }

            IChannel sessionChannel = getSessionChannelSafely();
            if (sessionChannel != null && sessionChannel.equals(user.getChannel())
                    && isAudibleTalkState(user.getTalkState())) {
                activeTalkers.put(user.getSession(), user.getName());
            } else {
                activeTalkers.remove(user.getSession());
            }
            showTrafficOrReady();
            maybeApplyPendingConfiguration();
        }

        @Override
        public void onPermissionDenied(String reason) {
            setStatus(COLOR_ERROR, getString(R.string.radio_access_denied));
            if (pendingConfigTrial) {
                failPendingConfiguration();
            }
        }
    };

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((MumlaService.MumlaBinder) binder).getService();
            service.registerObserver(observer);
            updateFromService();
            maybeApplyPendingConfiguration();
            maybeConnect();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
            pttButton.setEnabled(false);
            setStatus(COLOR_OFFLINE, getString(R.string.radio_service_stopped));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = Settings.getInstance(this);
        database = new MumlaSQLiteDatabase(this);
        database.open();
        buildUi();
        loadConfiguration();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!configReceiverRegistered) {
            ContextCompat.registerReceiver(this, configReceiver,
                    new IntentFilter(RadioConfigUpdater.ACTION_CONFIG_PENDING),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            configReceiverRegistered = true;
        }
        if (!bound) {
            bound = bindService(new Intent(this, MumlaService.class), serviceConnection,
                    BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        releasePtt();
        if (configReceiverRegistered) {
            unregisterReceiver(configReceiver);
            configReceiverRegistered = false;
        }
        if (bound) {
            if (service != null) {
                service.unregisterObserver(observer);
            }
            unbindService(serviceConnection);
            bound = false;
            service = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        database.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Intent recoveryDashboard = new Intent(this, MinimumHomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(recoveryDashboard);
        finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (RadioPttKeyManager.isConfiguredPttKey(keyCode, settings)
                && !RadioPttKeyManager.isMediaStyleKey(keyCode)) {
            if (event.getRepeatCount() == 0 && service != null) {
                service.onTalkKeyDown();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (event.getRepeatCount() == 0) {
                selectRelativeRoom(-1);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getRepeatCount() == 0) {
                selectRelativeRoom(1);
            }
            return true;
        }
        if (isConfirmKey(keyCode)) {
            if (event.getRepeatCount() == 0) {
                joinSelectedRoom();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_ENDCALL) {
            if (event.getRepeatCount() == 0 && config != null) {
                selectedRoomIndex = config.getDefaultRoomIndex();
                joinSelectedRoom();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (RadioPttKeyManager.isConfiguredPttKey(keyCode, settings)
                && !RadioPttKeyManager.isMediaStyleKey(keyCode)) {
            releasePtt();
            return true;
        }
        if (isNavigationKey(keyCode)) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MICROPHONE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                maybeConnect();
            } else {
                setStatus(COLOR_ERROR, getString(R.string.radio_microphone_required));
            }
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(5), dp(3), dp(5), dp(3));

        serviceNameView = textView(11, Color.LTGRAY);
        root.addView(serviceNameView, new LinearLayout.LayoutParams(-1, -2));

        statusView = textView(18, Color.WHITE);
        statusView.setGravity(android.view.Gravity.CENTER);
        statusView.setPadding(dp(2), dp(2), dp(2), dp(2));
        root.addView(statusView, new LinearLayout.LayoutParams(-1, dp(29)));

        roomView = textView(12, Color.WHITE);
        roomView.setGravity(android.view.Gravity.CENTER);
        root.addView(roomView, new LinearLayout.LayoutParams(-1, dp(20)));

        identityView = textView(9, Color.GRAY);
        identityView.setGravity(android.view.Gravity.CENTER);
        String deviceId = new DeviceIdentityManager(
                PreferenceManager.getDefaultSharedPreferences(this)).getOrCreateDeviceId();
        identityView.setText(deviceId + " · " + RadioDeviceProfile.detectCurrent());
        root.addView(identityView, new LinearLayout.LayoutParams(-1, dp(15)));

        pttButton = new Button(this);
        pttButton.setText(R.string.radio_ptt);
        pttButton.setTextSize(20);
        pttButton.setEnabled(false);
        pttButton.setOnTouchListener((view, event) -> {
            if (service == null || !pttButton.isEnabled()) {
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
        });
        root.addView(pttButton, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        setStatus(COLOR_OFFLINE, getString(R.string.radio_loading_config));
    }

    private TextView textView(int size, int color) {
        TextView view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setSingleLine(true);
        return view;
    }

    private void loadConfiguration() {
        new Thread(() -> {
            try {
                RadioConnectionConfig loaded = RadioConnectionConfig.fromJson(
                        new RadioConfigRepository(this).loadActiveOrDefault());
                runOnUiThread(() -> {
                    if (destroyed) {
                        return;
                    }
                    applyConfigurationToUi(loaded);
                    maybeApplyPendingConfiguration();
                    maybeConnect();
                });
            } catch (IOException | JSONException | RuntimeException error) {
                runOnUiThread(() -> setStatus(COLOR_ERROR,
                        getString(R.string.radio_config_invalid)));
            }
        }, "minimum-radio-startup").start();
    }

    private void applyConfigurationToUi(RadioConnectionConfig loaded) {
        config = loaded;
        selectedRoomIndex = loaded.getDefaultRoomIndex();
        serviceNameView.setText(loaded.getServiceName());
        roomView.setText(loaded.getDefaultRoom().getLabel());
        if (!loaded.isAutoConnect()) {
            setStatus(COLOR_OFFLINE, getString(R.string.radio_not_provisioned));
        }
    }

    /** Staged remote config is trialled only while neither local TX nor remote RX is active. */
    private void maybeApplyPendingConfiguration() {
        if (destroyed || config == null || service == null || pendingConfigTrial
                || pendingConfigIoInFlight) {
            return;
        }
        RadioConfigRepository repository = new RadioConfigRepository(this);
        if (!repository.hasPending()) {
            return;
        }
        if (!isRadioIdleForConfig()) {
            setStatus(COLOR_BUSY, getString(R.string.radio_waiting_for_idle));
            return;
        }

        pendingConfigIoInFlight = true;
        setStatus(COLOR_BUSY, getString(R.string.radio_config_testing));
        new Thread(() -> {
            try {
                JSONObject pending = repository.loadPending();
                RadioConnectionConfig candidate = pending == null
                        ? null : RadioConnectionConfig.fromJson(pending);
                runOnUiThread(() -> {
                    pendingConfigIoInFlight = false;
                    if (destroyed || candidate == null) {
                        return;
                    }
                    beginPendingConfigurationTrial(candidate);
                });
            } catch (IOException | JSONException | RuntimeException error) {
                try {
                    repository.discardPending();
                } catch (IOException ignored) {
                    // The next startup will retry or reject the same pending file safely.
                }
                runOnUiThread(() -> {
                    pendingConfigIoInFlight = false;
                    setStatus(COLOR_ERROR, getString(R.string.radio_config_invalid));
                });
            }
        }, "minimum-radio-config-load-pending").start();
    }

    private boolean isRadioIdleForConfig() {
        if (service == null) {
            return false;
        }
        HumlaService.ConnectionState state = service.getConnectionState();
        boolean transitioning = state == HumlaService.ConnectionState.CONNECTING
                || state == HumlaService.ConnectionState.CONNECTION_LOST;
        boolean transmitting = false;
        try {
            transmitting = service.isConnected() && service.HumlaSession().isTalking();
        } catch (IllegalStateException ignored) {
            return false;
        }
        return RadioConfigActivationPolicy.canTrial(true, transitioning, transmitting,
                service.isRadioReceiving());
    }

    private static boolean isAudibleTalkState(TalkState state) {
        return state == TalkState.TALKING || state == TalkState.SHOUTING
                || state == TalkState.WHISPERING;
    }

    private void beginPendingConfigurationTrial(RadioConnectionConfig candidate) {
        pendingConfigTrial = true;
        applyConfigurationToUi(candidate);
        setStatus(COLOR_BUSY, getString(R.string.radio_config_testing));

        if (!candidate.isAutoConnect()) {
            commitPendingConfiguration();
            return;
        }
        if (service != null && service.isConnected()) {
            reconnectAfterDisconnect = true;
            service.disconnect();
        } else {
            maybeConnect();
        }
    }

    private void commitPendingConfiguration() {
        if (!pendingConfigTrial || pendingConfigIoInFlight) {
            return;
        }
        pendingConfigIoInFlight = true;
        RadioConfigRepository repository = new RadioConfigRepository(this);
        new Thread(() -> {
            try {
                repository.commitPending();
                runOnUiThread(() -> {
                    pendingConfigIoInFlight = false;
                    pendingConfigTrial = false;
                    if (destroyed) {
                        return;
                    }
                    if (!config.isAutoConnect() && service != null && service.isConnected()) {
                        reconnectAfterDisconnect = false;
                        service.disconnect();
                        setStatus(COLOR_OFFLINE, getString(R.string.radio_not_provisioned));
                    } else {
                        showTrafficOrReady();
                    }
                });
            } catch (IOException | JSONException | RuntimeException error) {
                runOnUiThread(() -> {
                    pendingConfigIoInFlight = false;
                    failPendingConfiguration();
                });
            }
        }, "minimum-radio-config-commit").start();
    }

    private void failPendingConfiguration() {
        if (!pendingConfigTrial || pendingConfigIoInFlight) {
            return;
        }
        pendingConfigIoInFlight = true;
        RadioConfigRepository repository = new RadioConfigRepository(this);
        new Thread(() -> {
            RadioConnectionConfig restored = null;
            try {
                repository.discardPending();
                restored = RadioConnectionConfig.fromJson(repository.loadActiveOrDefault());
            } catch (IOException | JSONException | RuntimeException ignored) {
                // The current in-memory LKG remains safer than accepting the failed candidate.
            }
            RadioConnectionConfig finalRestored = restored;
            runOnUiThread(() -> {
                pendingConfigIoInFlight = false;
                pendingConfigTrial = false;
                if (destroyed) {
                    return;
                }
                if (finalRestored == null) {
                    setStatus(COLOR_ERROR, getString(R.string.radio_config_invalid));
                    return;
                }
                applyConfigurationToUi(finalRestored);
                setStatus(COLOR_ERROR, getString(R.string.radio_config_rolled_back));
                if (service != null && (service.isConnected() || service.isReconnecting()
                        || service.getConnectionState() == HumlaService.ConnectionState.CONNECTING)) {
                    reconnectAfterDisconnect = finalRestored.isAutoConnect();
                    service.disconnect();
                } else {
                    maybeConnect();
                }
            });
        }, "minimum-radio-config-rollback").start();
    }

    private boolean shouldRejectPendingTrial(HumlaException error) {
        if (error == null) {
            return false;
        }
        switch (error.getReason()) {
            case REJECT:
            case OTHER_ERROR:
                return true;
            case CONNECTION_ERROR:
                return RadioConfigUpdater.isNetworkConnected(this);
            case USER_REMOVE:
            default:
                return false;
        }
    }

    private void maybeConnect() {
        if (destroyed || config == null || service == null || connectRequested
                || pendingConfigIoInFlight || !config.isAutoConnect()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    MICROPHONE_PERMISSION_REQUEST);
            return;
        }

        HumlaService.ConnectionState state = service.getConnectionState();
        if (state == HumlaService.ConnectionState.CONNECTING
                || (state == HumlaService.ConnectionState.CONNECTION_LOST
                && service.isReconnecting())) {
            updateFromService();
            return;
        }
        if (service.isConnected()) {
            Server target = service.getTargetServer();
            if (target != null && config.getHost().equalsIgnoreCase(target.getHost())
                    && config.getPort() == target.getPort()) {
                joinSelectedRoom();
                updateFromService();
                return;
            }
            IHumlaSession session = service.HumlaSession();
            if (session.isTalking()) {
                setStatus(COLOR_BUSY, getString(R.string.radio_waiting_for_idle));
                return;
            }
            reconnectAfterDisconnect = true;
            service.disconnect();
            return;
        }

        connectRequested = true;
        setStatus(COLOR_BUSY, getString(R.string.radio_preparing_identity));
        RadioCertificateManager.ensureCertificate(this, available -> {
            if (!available || destroyed) {
                connectRequested = false;
                setStatus(COLOR_ERROR, getString(R.string.radio_certificate_failed));
                return;
            }
            String deviceId = new DeviceIdentityManager(
                    PreferenceManager.getDefaultSharedPreferences(this)).getOrCreateDeviceId();
            Server server = new Server(-1, config.getServiceName(), config.getHost(),
                    config.getPort(), deviceId, "");
            new ServerConnectTask(this, database, config.getAccessTokens(),
                    config.isAutoReconnect()).execute(server);
        });
    }

    private void updateFromService() {
        if (service == null) {
            return;
        }
        switch (service.getConnectionState()) {
            case CONNECTED:
                IChannel channel = getSessionChannelSafely();
                if (channel != null) {
                    roomView.setText(RoomPathResolver.fullPath(channel));
                }
                pttButton.setEnabled(true);
                showTrafficOrReady();
                break;
            case CONNECTING:
                pttButton.setEnabled(false);
                setStatus(COLOR_BUSY, getString(R.string.radio_connecting));
                break;
            case CONNECTION_LOST:
                pttButton.setEnabled(false);
                setStatus(service.isReconnecting() ? COLOR_BUSY : COLOR_ERROR,
                        getString(service.isReconnecting()
                                ? R.string.radio_reconnecting : R.string.radio_offline));
                break;
            case DISCONNECTED:
            default:
                pttButton.setEnabled(false);
                if (config == null || config.isAutoConnect()) {
                    setStatus(COLOR_OFFLINE, getString(R.string.radio_offline));
                }
                break;
        }
    }

    private void trustConfiguredCertificate(X509Certificate[] chain) {
        if (config == null || chain == null || chain.length == 0) {
            setStatus(COLOR_ERROR, getString(R.string.radio_certificate_untrusted));
            if (pendingConfigTrial) {
                failPendingConfiguration();
            }
            return;
        }
        try {
            String actual = toHex(MessageDigest.getInstance("SHA-256")
                    .digest(chain[0].getEncoded()));
            if (!config.acceptsServerCertificate(actual)) {
                if (config.getServerCertificateSha256() == null) {
                    setStatus(COLOR_ERROR, getString(R.string.radio_certificate_untrusted));
                    if (pendingConfigTrial) {
                        failPendingConfiguration();
                    }
                    return;
                }
                setStatus(COLOR_ERROR, getString(R.string.radio_certificate_changed));
                if (pendingConfigTrial) {
                    failPendingConfiguration();
                }
                return;
            }
            KeyStore trustStore = MumlaTrustStore.getTrustStore(this);
            String alias = "minimum-" + config.getHost() + ":" + config.getPort();
            trustStore.setCertificateEntry(alias, chain[0]);
            MumlaTrustStore.saveTrustStore(this, trustStore);
            retryAfterConfiguredCertificateTrust = true;
            setStatus(COLOR_BUSY, getString(R.string.radio_certificate_trusted));
        } catch (Exception error) {
            setStatus(COLOR_ERROR, getString(R.string.radio_certificate_failed));
            if (pendingConfigTrial) {
                failPendingConfiguration();
            }
        }
    }

    private static String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format(java.util.Locale.ROOT, "%02X", item & 0xFF));
        }
        return result.toString();
    }

    private void selectRelativeRoom(int direction) {
        if (config == null || config.getRooms().isEmpty()) {
            return;
        }
        int count = config.getRooms().size();
        selectedRoomIndex = (selectedRoomIndex + direction + count) % count;
        RadioConnectionConfig.Room room = config.getRooms().get(selectedRoomIndex);
        roomView.setText(room.getLabel());
        setStatus(COLOR_BUSY, getString(R.string.radio_press_ok));
    }

    private void joinSelectedRoom() {
        if (config == null || service == null || !service.isConnected()) {
            return;
        }
        IHumlaSession session = service.HumlaSession();
        if (session.isTalking()) {
            setStatus(COLOR_BUSY, getString(R.string.radio_waiting_for_idle));
            return;
        }
        RadioConnectionConfig.Room room = config.getRooms().get(selectedRoomIndex);
        IChannel target = RoomPathResolver.resolve(session.getRootChannel(), room.getPath());
        if (target == null) {
            setStatus(COLOR_ERROR, getString(R.string.radio_room_missing));
            if (pendingConfigTrial) {
                failPendingConfiguration();
            }
            return;
        }
        roomView.setText(room.getLabel());
        if (!target.equals(session.getSessionChannel())) {
            session.joinChannel(target.getId());
        } else {
            showReadyState();
            if (pendingConfigTrial) {
                commitPendingConfiguration();
            }
        }
    }

    private boolean isSelectedRoom(IChannel channel) {
        if (config == null || channel == null) {
            return false;
        }
        return config.getRooms().get(selectedRoomIndex).getPath()
                .equals(RoomPathResolver.fullPath(channel));
    }

    private void showTrafficOrReady() {
        if (activeTalkers.isEmpty()) {
            showReadyState();
        } else if (activeTalkers.size() == 1) {
            setStatus(COLOR_READY, activeTalkers.values().iterator().next());
        } else {
            setStatus(COLOR_READY, getString(R.string.radio_multiple_talkers));
        }
    }

    private void showReadyState() {
        pttButton.setEnabled(service != null && service.isConnected());
        setStatus(COLOR_READY, getString(R.string.radio_ready));
    }

    private boolean isSelf(IUser user) {
        if (service == null || user == null || !service.isConnected()) {
            return false;
        }
        try {
            return user.getSession() == service.HumlaSession().getSessionId();
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private IChannel getSessionChannelSafely() {
        if (service == null || !service.isConnected()) {
            return null;
        }
        try {
            return service.HumlaSession().getSessionChannel();
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private void releasePtt() {
        if (service != null) {
            service.onTalkKeyUp();
        }
    }

    private void setStatus(int color, String text) {
        statusView.setBackgroundColor(color);
        statusView.setText(text);
    }

    private boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                || keyCode == KeyEvent.KEYCODE_BUTTON_SELECT
                || keyCode == KeyEvent.KEYCODE_CALL;
    }

    private boolean isNavigationKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_UP
                || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_ENDCALL
                || isConfirmKey(keyCode);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
