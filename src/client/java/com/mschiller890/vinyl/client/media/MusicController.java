package com.mschiller890.vinyl.client.media;

import com.mschiller890.vinyl.client.hud.VinylHud;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Singleton orchestrator for the Windows media integration. (big words huh)
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Owns a background daemon thread that polls the active GSMTC session
 *       at a low frequency (~2&nbsp;Hz) and pushes metadata updates to the
 *       HUD on the Minecraft client thread.</li>
 *   <li>Implements {@link MediaButtonHandler} so the inventory-screen buttons
 *       can drive playback. Transport commands are sent through the GSMTC
 *       session when available, and fall back to global media key events
 *       otherwise.</li>
 *   <li>Manages the lifecycle of the {@link WindowsMediaTransport} host
 *       process and restarts it if it dies.</li>
 * </ul>
 *
 * <p>The controller is intentionally tolerant of failure: if the GSMTC
 * host cannot start (e.g. on a system without the required Windows
 * runtime, or while running outside Windows), it degrades to media-key
 * transport control and shows a "no metadata" state on the HUD.</p>
 */
public final class MusicController implements MediaButtonHandler {

    private static final MusicController INSTANCE = new MusicController();

    private static final long POLL_INTERVAL_MS = 500L;
    private static final long RESTART_BACKOFF_MS = 5000L;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CopyOnWriteArrayList<MediaSessionListener> listeners = new CopyOnWriteArrayList<>();
    private final WindowsMediaTransport transport = new WindowsMediaTransport();

    private Thread pollThread;
    private volatile boolean usingFallback = false;
    private volatile MediaMetadata lastMetadata;
    private volatile boolean transportAvailable = false;

    private MusicController() {
    }

    public static MusicController getInstance() {
        return INSTANCE;
    }

    // idk how to make regions in vscode
    /* Lifecycle */

    public void initialize() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        System.out.println("[Vinyl] MusicController initializing...");
        transportAvailable = transport.start();
        usingFallback = !transportAvailable;
        System.out.println("[Vinyl] GSMTC transport available: " + transportAvailable
                + (usingFallback ? " (using media-key fallback)" : ""));

        pollThread = new Thread(this::pollLoop, "Vinyl-MediaPoll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        transport.stop();
        Thread t = pollThread;
        pollThread = null;
        if (t != null) {
            t.interrupt();
        }
        // Clear the HUD.
        Minecraft.getInstance().execute(() -> VinylHud.setSong("", ""));
    }

    /* Listeners */

    public void addListener(MediaSessionListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(MediaSessionListener listener) {
        listeners.remove(listener);
    }

    /* MediaButtonHandler */

    @Override
    public void previous() {
        if (transportAvailable) {
            transport.sendPrevious();
        }
        // Always also send the media key -- this covers apps that don't
        // honor GSMTC transport commands (e.g. some Firefox configurations)
        // and ensures the button "feels" responsive even when the GSMTC
        // session is stale.
        MediaKeySender.sendPrevious();
    }

    @Override
    public void playPause() {
        if (transportAvailable) {
            // GSMTC doesn't expose a single "toggle" -- we read the current
            // state and send the matching play/pause command.
            MediaMetadata current = lastMetadata;
            if (current != null && current.playbackState() == MediaMetadata.PlaybackState.PLAYING) {
                transport.sendPause();
            } else {
                transport.sendPlay();
            }
        }
        MediaKeySender.sendPlayPause();
    }

    @Override
    public void next() {
        if (transportAvailable) {
            transport.sendNext();
        }
        MediaKeySender.sendNext();
    }

    @Override
    public boolean isUsingFallback() {
        return usingFallback;
    }

    /* Polling */

    private void pollLoop() {
        while (running.get()) {
            try {
                if (!transport.isAlive()) {
                    // Host died -- try to restart it.
                    transport.stop();
                    transportAvailable = transport.start();
                    usingFallback = !transportAvailable;
                    if (!transportAvailable) {
                        Thread.sleep(RESTART_BACKOFF_MS);
                        continue;
                    }
                }

                MediaMetadata metadata = transport.query();
                if (metadata == null) {
                    // Communication hiccup -- leave HUD as-is and retry next tick.
                    Thread.sleep(POLL_INTERVAL_MS);
                    continue;
                }

                if (!metadata.equals(lastMetadata)) {
                    lastMetadata = metadata;
                    System.out.println("[Vinyl] Media update: " + metadata.title()
                            + " / " + metadata.artist() + " / " + metadata.playbackState());
                    notifyListeners(metadata);
                    updateHud(metadata);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                // Never let the poll loop die from an unexpected exception.
                System.err.println("[Vinyl] Media poll error: " + t.getMessage());
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void notifyListeners(MediaMetadata metadata) {
        for (MediaSessionListener listener : listeners) {
            try {
                listener.onSessionChanged(metadata);
            } catch (Throwable t) {
                System.err.println("[Vinyl] Listener threw: " + t.getMessage());
            }
        }
    }

    private void updateHud(MediaMetadata metadata) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            if (!metadata.hasContent()) {
                if (metadata.playbackState() == MediaMetadata.PlaybackState.CLOSED) {
                    VinylHud.setSong("No song playing", "Is a streaming service running?");
                } else {
                    VinylHud.setSong("", "");
                }
                return;
            }
            String title = metadata.title();
            String artist = metadata.artist();
            if (artist == null || artist.isBlank()) {
                artist = metadata.sourceAppId() != null ? metadata.sourceAppId() : "";
            }
            VinylHud.setSong(title, artist);
        });
    }
}