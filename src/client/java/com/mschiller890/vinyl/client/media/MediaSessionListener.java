package com.mschiller890.vinyl.client.media;

/**
 * Receives notifications when the active media session changes.
 *
 * <p>Implementations are called from a background monitoring thread.
 * Any work that must touch the Minecraft client thread (e.g. updating
 * the HUD) should be scheduled back onto the client thread from within
 * the callback.</p>
 */
@FunctionalInterface
public interface MediaSessionListener {

    /**
     * Called whenever the active session's metadata or playback state
     * changes, or when no session is active.
     *
     * @param metadata snapshot of the current session, or a metadata
     *                 object with empty fields and {@link
     *                 MediaMetadata.PlaybackState#CLOSED} if no session
     *                 is active
     */
    void onSessionChanged(MediaMetadata metadata);
}
