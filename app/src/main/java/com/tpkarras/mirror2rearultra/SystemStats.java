package com.tpkarras.mirror2rearultra;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Environment;
import android.os.StatFs;

/**
 * The counters the panel can show about the phone itself.
 *
 * <p>Pulled out of {@link RearDashboardController} so the builder's preview can
 * read the same three. It used to invent nothing for them at all, which meant
 * the network, memory and storage widgets counted as having no data and were
 * dropped: a page made of them looked empty in the preview while the panel
 * showed it perfectly well.
 *
 * <p>None of this needs a permission or a running service, so the preview shows
 * the real readings rather than a stand-in.
 */
final class SystemStats {

    /** Empty when there is no connection to describe. */
    final String networkSummary;
    /** Percentage in use, or -1 when it cannot be read. */
    final int memoryPercent;
    /** Percentage free, or -1 when it cannot be read. */
    final int storagePercentFree;

    private SystemStats(String networkSummary, int memoryPercent, int storagePercentFree) {
        this.networkSummary = networkSummary;
        this.memoryPercent = memoryPercent;
        this.storagePercentFree = storagePercentFree;
    }

    static SystemStats read(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities capabilities = connectivity == null ? null
                : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        String network;
        if (capabilities == null) {
            network = context.getString(R.string.dashboard_network_offline);
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            network = context.getString(R.string.dashboard_network_wifi);
        } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            network = context.getString(R.string.dashboard_network_mobile);
        } else {
            network = context.getString(R.string.dashboard_network_connected);
        }

        int memory = -1;
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            memory = info.totalMem <= 0 ? -1
                    : Math.round((info.totalMem - info.availMem) * 100f / info.totalMem);
        }

        StatFs storage = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        int free = storage.getTotalBytes() <= 0 ? -1
                : Math.round(storage.getAvailableBytes() * 100f / storage.getTotalBytes());

        return new SystemStats(network, memory, free);
    }
}
