package com.mschiller890.vinyl.client.media;

/**
 * Immutable snapshot of metadata for a single media session.
 *
 * <p>Captured from the Windows Global System Media Transport Controls
 * (GSMTC) API and passed to listeners whenever the active session's
 * metadata or playback state changes.</p>
 */
public final class MediaMetadata {

    /** Playback state reported by the session. */
    public enum PlaybackState {
        PLAYING,
        PAUSED,
        STOPPED,
        CHANGING,
        CLOSED,
        UNKNOWN
    }

    private final String title;
    private final String artist;
    private final String album;
    private final PlaybackState playbackState;
    private final String sourceAppId;

    public MediaMetadata(String title, String artist, String album,
                         PlaybackState playbackState, String sourceAppId) {
        this.title = title != null ? title : "";
        this.artist = artist != null ? artist : "";
        this.album = album != null ? album : "";
        this.playbackState = playbackState != null ? playbackState : PlaybackState.UNKNOWN;
        this.sourceAppId = sourceAppId != null ? sourceAppId : "";
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    public String album() {
        return album;
    }

    public PlaybackState playbackState() {
        return playbackState;
    }

    public String sourceAppId() {
        return sourceAppId;
    }

    /**
     * @return {@code true} if this session has a non-empty title, i.e. it
     * represents actual media content rather than an idle browser tab.
     */
    public boolean hasContent() {
        return !title.isEmpty();
    }

    @Override
    public String toString() {
        return "MediaMetadata{title='" + title + "', artist='" + artist
                + "', album='" + album + "', state=" + playbackState
                + ", source='" + sourceAppId + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MediaMetadata that)) return false;
        return title.equals(that.title)
                && artist.equals(that.artist)
                && album.equals(that.album)
                && playbackState == that.playbackState
                && sourceAppId.equals(that.sourceAppId);
    }

    @Override
    public int hashCode() {
        int result = title.hashCode();
        result = 31 * result + artist.hashCode();
        result = 31 * result + album.hashCode();
        result = 31 * result + playbackState.hashCode();
        result = 31 * result + sourceAppId.hashCode();
        return result;
    }
}
