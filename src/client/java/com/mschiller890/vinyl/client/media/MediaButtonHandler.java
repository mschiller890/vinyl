package com.mschiller890.vinyl.client.media;

/**
 * Transport controls for a single media session.
 *
 * <p>Implementations map these calls onto the underlying Windows API:
 * either the GSMTC session's transport controls (when available) or
 * synthesized media key events as a fallback.</p>
 */
public interface MediaButtonHandler {

    void previous();
    void playPause();
    void next();

    /**
     * @return {@code true} if this handler can send real transport
     * commands to the active session; {@code false} if it is falling
     * back to media key events
     */
    boolean isUsingFallback();
}
