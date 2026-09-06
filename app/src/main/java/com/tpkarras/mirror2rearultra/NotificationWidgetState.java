package com.tpkarras.mirror2rearultra;

final class NotificationWidgetState {
    private static volatile int count;
    private NotificationWidgetState() {}
    static int getCount() { return count; }
    static void setCount(int value) { count = Math.max(0, value); }
}
