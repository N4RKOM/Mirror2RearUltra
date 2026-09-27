package com.tpkarras.mirror2rearultra;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which Bluetooth devices are connected and which Wi-Fi network the phone is
 * on, for the triggers that wait on them.
 *
 * <p>Each half runs only while a trigger asks for it: nothing is watched for
 * a trigger nobody set.
 *
 * <p>Bluetooth has no single "what is connected" to ask. Connections are
 * followed as they come and go, and the ones already up when this starts are
 * gathered from the audio profiles - a car kit or a headset - and from Bluetooth
 * LE, which is where a watch sits.
 *
 * <p>The network's name is given only to an app allowed the precise location
 * and with location switched on; without them the phone is still known to be
 * on Wi-Fi, just not which, so "any network" keeps working.
 */
final class PanelSurroundings {
    interface Listener {
        void onSurroundingsChanged();
    }

    private static final int[] AUDIO_PROFILES = {BluetoothProfile.A2DP, BluetoothProfile.HEADSET};
    private static final String UNKNOWN_SSID = "<unknown ssid>";

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> connected = new HashSet<>();
    /** Each open profile proxy, with the profile it was opened for, to close it by. */
    private final Map<BluetoothProfile, Integer> proxies = new HashMap<>();
    private boolean watchingBluetooth;
    private boolean watchingWifi;
    private boolean onWifi;
    @Nullable private String network;
    @Nullable private ConnectivityManager.NetworkCallback wifiCallback;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON)
                        != BluetoothAdapter.STATE_ON) {
                    changeConnected(Collections.emptySet(), true);
                }
                return;
            }
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (device == null) {
                return;
            }
            Set<String> next = new HashSet<>(connected);
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                next.add(device.getAddress());
            } else {
                next.remove(device.getAddress());
            }
            changeConnected(next, true);
        }
    };

    PanelSurroundings(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** Starts or stops each half to match which triggers are set. */
    void configure(boolean bluetooth, boolean wifi) {
        if (bluetooth != watchingBluetooth) {
            if (bluetooth) startBluetooth(); else stopBluetooth();
        }
        if (wifi != watchingWifi) {
            if (wifi) startWifi(); else stopWifi();
        }
    }

    void stop() {
        configure(false, false);
    }

    Set<String> connectedDevices() {
        return Collections.unmodifiableSet(connected);
    }

    boolean isOnWifi() {
        return onWifi;
    }

    /** The network's name, or null when not on one or not allowed to know. */
    @Nullable
    String wifiNetwork() {
        return network;
    }

    static boolean canSeeBluetooth(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ContextCompat.checkSelfPermission(context,
                Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void startBluetooth() {
        watchingBluetooth = true;
        if (!canSeeBluetooth(context)) {
            return;
        }
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        ContextCompat.registerReceiver(context, bluetoothReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return;
        }
        try {
            Set<String> already = new HashSet<>(connected);
            for (BluetoothDevice device : manager.getConnectedDevices(BluetoothProfile.GATT)) {
                already.add(device.getAddress());
            }
            changeConnected(already, false);
            for (int profile : AUDIO_PROFILES) {
                adapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int which, BluetoothProfile proxy) {
                        main.post(() -> addFromProxy(which, proxy));
                    }

                    @Override
                    public void onServiceDisconnected(int which) {
                    }
                }, profile);
            }
        } catch (SecurityException refused) {
            // The permission went between the check and the call.
        }
    }

    private void addFromProxy(int profile, BluetoothProfile proxy) {
        if (!watchingBluetooth) {
            closeProxy(profile, proxy);
            return;
        }
        proxies.put(proxy, profile);
        try {
            Set<String> next = new HashSet<>(connected);
            for (BluetoothDevice device : proxy.getConnectedDevices()) {
                next.add(device.getAddress());
            }
            changeConnected(next, true);
        } catch (SecurityException refused) {
            // Nothing learned; the connection broadcasts still come.
        }
    }

    private void stopBluetooth() {
        watchingBluetooth = false;
        try {
            context.unregisterReceiver(bluetoothReceiver);
        } catch (IllegalArgumentException never) {
            // Not registered: the permission was missing when it started.
        }
        for (Map.Entry<BluetoothProfile, Integer> open : proxies.entrySet()) {
            closeProxy(open.getValue(), open.getKey());
        }
        proxies.clear();
        connected.clear();
    }

    private void closeProxy(int profile, BluetoothProfile proxy) {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter != null) {
            adapter.closeProfileProxy(profile, proxy);
        }
    }

    private void changeConnected(Set<String> next, boolean announce) {
        if (next.equals(connected)) {
            return;
        }
        connected.clear();
        connected.addAll(next);
        if (announce) {
            listener.onSurroundingsChanged();
        }
    }

    private void startWifi() {
        watchingWifi = true;
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity == null) {
            return;
        }
        ConnectivityManager.NetworkCallback callback = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? new WifiCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO)
                : new WifiCallback();
        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        try {
            connectivity.registerNetworkCallback(request, callback, main);
            wifiCallback = callback;
        } catch (RuntimeException refused) {
            wifiCallback = null;
        }
    }

    private void stopWifi() {
        watchingWifi = false;
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        if (connectivity != null && wifiCallback != null) {
            try {
                connectivity.unregisterNetworkCallback(wifiCallback);
            } catch (IllegalArgumentException never) {
                // Already gone.
            }
        }
        wifiCallback = null;
        onWifi = false;
        network = null;
    }

    private void changeWifi(boolean nowOnWifi, @Nullable String nowNetwork) {
        if (nowOnWifi == onWifi && (nowNetwork == null ? network == null
                : nowNetwork.equals(network))) {
            return;
        }
        onWifi = nowOnWifi;
        network = nowNetwork;
        listener.onSurroundingsChanged();
    }

    /** The name without the quotes the system wraps it in, or null for none. */
    @Nullable
    static String cleanNetworkName(@Nullable String ssid) {
        if (ssid == null || ssid.isEmpty() || UNKNOWN_SSID.equals(ssid)) {
            return null;
        }
        if (ssid.length() >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    private final class WifiCallback extends ConnectivityManager.NetworkCallback {
        WifiCallback() {
            super();
        }

        WifiCallback(int flags) {
            super(flags);
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network ignored,
                @NonNull NetworkCapabilities capabilities) {
            String name = null;
            if (capabilities.getTransportInfo() instanceof WifiInfo) {
                name = cleanNetworkName(((WifiInfo) capabilities.getTransportInfo()).getSSID());
            }
            changeWifi(true, name);
        }

        @Override
        public void onLost(@NonNull Network ignored) {
            changeWifi(false, null);
        }
    }
}
