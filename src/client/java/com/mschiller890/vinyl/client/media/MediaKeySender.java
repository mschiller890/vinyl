package com.mschiller890.vinyl.client.media;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

/**
 * Sends synthetic media key events via the Windows {@code user32} API.
 *
 * <p>This is the universal fallback for transport controls: every media
 * application that registers a global hotkey handler for media keys
 * (Chrome, Edge, Brave, Opera, Vivaldi, Firefox, Spotify, etc.) will
 * respond to these events. It does <em>not</em> require the application
 * to expose a GSMTC session, so it works even when metadata reading is
 * unavailable.</p>
 *
 * <p>Because media keys are global, they control whichever application
 * currently owns the media session -- which is exactly the behavior we
 * want for a "now playing" overlay.</p>
 * 
 * <p>my god.</p>
 */
final class MediaKeySender {

    /** Virtual key codes for media keys. */
    private static final int VK_MEDIA_PLAY_PAUSE = 0xB3;
    private static final int VK_MEDIA_PREV_TRACK = 0xB1;
    private static final int VK_MEDIA_NEXT_TRACK = 0xB0;

    private interface ExtendedUser32 extends User32, StdCallLibrary {
        ExtendedUser32 INSTANCE = Native.load("user32", ExtendedUser32.class, W32APIOptions.DEFAULT_OPTIONS);

        /**
         * Synthesizes a keystroke via the legacy {@code keybd_event} API.
         * Works reliably for media keys across all Windows versions.
         */
        void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
    }

    private static final ExtendedUser32 USER32 = ExtendedUser32.INSTANCE;

    private static final int KEYEVENTF_KEYUP = 0x0002;

    private MediaKeySender() {
    }

    static void sendPlayPause() {
        sendKey(VK_MEDIA_PLAY_PAUSE);
    }

    static void sendPrevious() {
        sendKey(VK_MEDIA_PREV_TRACK);
    }

    static void sendNext() {
        sendKey(VK_MEDIA_NEXT_TRACK);
    }

    private static void sendKey(int vk) {
        try {
            USER32.keybd_event((byte) vk, (byte) 0, 0, 0);
            USER32.keybd_event((byte) vk, (byte) 0, KEYEVENTF_KEYUP, 0);
        } catch (Throwable t) {
            System.err.println("[Vinyl] Failed to send media key: " + t.getMessage());
        }
    }
}
