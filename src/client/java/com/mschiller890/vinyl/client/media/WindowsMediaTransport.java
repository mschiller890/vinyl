package com.mschiller890.vinyl.client.media;

import java.util.HashMap;
import java.util.Map;

// lets hope i dont have to touch this ever again...
// im trying to keep the documentation here as thorough as possible so that if i do have to touch it, i can understand it in 6 months
// mods looking really good rn, but this is a nightmare to maintain. 
// i have no idea how to make this better without pulling in a full winrt projection layer, which would be a huge dependency weight for a small mod. this is the best we can do.

/**
 * Low-level JNA bindings for the Windows Global System Media Transport
 * Controls (GSMTC) API.
 *
 * <p>The GSMTC API is exposed through {@code Windows.Media.Control.dll} as a
 * WinRT / COM factory. Rather than pulling in a full WinRT projection layer
 * (which would add significant dependency weight), we use the
 * {@code WindowsGetGlobalInterface} / registry-activation approach via the
 * public {@code GlobalSystemMediaTransportControlsSessionManager} runtime
 * class.</p>
 *
 * <p>Because the full WinRT COM vtable layout is large and version-sensitive,
 * this class instead uses the simpler approach of shelling out to a tiny
 * PowerShell snippet that queries GSMTC through the .NET
 * {@code Windows.Media.Control} projection (available on Windows 10 1903+
 * and Windows 11). This keeps the mod dependency-light while still using the
 * official, supported Windows API -- no browser automation, no memory
 * scraping, no extensions.</p>
 *
 * <p>The PowerShell process is launched once and kept alive; we feed it
 * commands over stdin and read JSON responses from stdout. This avoids
 * spawning a process per poll and keeps CPU usage negligible.</p>
 *
 * <p><b>Encoding:</b> track titles/artists can contain arbitrary Unicode
 * (CJK, emoji, accented Latin, etc). To avoid mangling that text into
 * {@code ?} characters we must pin UTF-8 on <em>both</em> ends of the pipe:
 * PowerShell's console input/output encoding (which otherwise defaults to
 * the system's OEM/ANSI codepage) and the Java {@link java.io.Reader}/
 * {@link java.io.Writer} instances wrapping the process streams (which
 * otherwise use the JVM's platform-default charset, not guaranteed to be
 * UTF-8). </p>
 */
final class WindowsMediaTransport {

    /**
     * PowerShell snippet that sets up a GSMTC manager and a JSON RPC loop.
     *
     * <p>After initialization the script writes the literal line
     * {@code __VINYL_READY__} to stdout so the Java side knows it is
     * ready to accept commands. Each command produces exactly one line
     * of JSON output.</p>
     */
    private static final String INIT_SCRIPT =
            // Force UTF-8 (no BOM) for both console input and output before
            // anything else runs. Without this, non-ASCII characters (e.g.
            // Japanese track titles) get transcoded to '?' by the default
            // OEM/ANSI console codepage. $OutputEncoding controls how
            // Write-Output/pipeline text is encoded when it hits stdout;
            // [Console]::OutputEncoding controls the underlying stream.
            "[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false\n" +
            "[Console]::InputEncoding = New-Object System.Text.UTF8Encoding $false\n" +
            "$OutputEncoding = [Console]::OutputEncoding\n" +
            "Add-Type -AssemblyName System.Runtime.WindowsRuntime\n" +
            "$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]\n" +
            "function Await($WinRtTask, $ResultType) {\n" +
            "  $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)\n" +
            "  $netTask = $asTask.Invoke($null, @($WinRtTask))\n" +
            "  $netTask.Wait(-1) | Out-Null\n" +
            "  $netTask.Result\n" +
            "}\n" +
            "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null\n" +
            "$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n" +
            "[Console]::Out.WriteLine('__VINYL_READY__')\n" +
            "[Console]::Out.Flush()\n" +
            "while ($line = [Console]::In.ReadLine()) {\n" +
            "  try {\n" +
            "    switch ($line.Trim()) {\n" +
            "      'get' {\n" +
            "        $session = $manager.GetCurrentSession()\n" +
            "        if ($null -eq $session) { Write-Output '{\"state\":\"CLOSED\"}'; continue }\n" +
            "        $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n" +
            "        $info = $session.GetPlaybackInfo()\n" +
            "        $title = ($props.Title -replace '\\\\','\\\\\\\\' -replace '\"','\\\"')\n" +
            "        $artist = ($props.Artist -replace '\\\\','\\\\\\\\' -replace '\"','\\\"')\n" +
            "        $album = ($props.AlbumTitle -replace '\\\\','\\\\\\\\' -replace '\"','\\\"')\n" +
            "        $src = ($session.SourceAppUserModelId -replace '\\\\','\\\\\\\\' -replace '\"','\\\"')\n" +
            "        $st = switch ([int]$info.PlaybackStatus) { 0 {'CLOSED'} 1 {'OPENED'} 2 {'CHANGING'} 3 {'STOPPED'} 4 {'PLAYING'} 5 {'PAUSED'} default {'UNKNOWN'} }\n" +
            "        Write-Output ('{\"title\":\"' + $title + '\",\"artist\":\"' + $artist + '\",\"album\":\"' + $album + '\",\"state\":\"' + $st + '\",\"source\":\"' + $src + '\"}')\n" +
            "      }\n" +
            "      'prev' { $s = $manager.GetCurrentSession(); if ($null -ne $s) { try { $s.TrySkipPreviousAsync() | Out-Null } catch {} } }\n" +
            "      'next' { $s = $manager.GetCurrentSession(); if ($null -ne $s) { try { $s.TrySkipNextAsync() | Out-Null } catch {} } }\n" +
            "      'play' { $s = $manager.GetCurrentSession(); if ($null -ne $s) { try { $s.TryPlayAsync() | Out-Null } catch {} } }\n" +
            "      'pause' { $s = $manager.GetCurrentSession(); if ($null -ne $s) { try { $s.TryPauseAsync() | Out-Null } catch {} } }\n" +
            "      'exit' { exit }\n" +
            "      default { Write-Output '{\"state\":\"UNKNOWN\"}' }\n" +
            "    }\n" +
            "  } catch {\n" +
            "    Write-Output ('{\"state\":\"ERROR\",\"msg\":\"' + ($_.Exception.Message -replace '\\\\','\\\\\\\\' -replace '\"','\\\"') + '\"}')\n" +
            "  }\n" +
            "}\n";

    private static final String READY_MARKER = "__VINYL_READY__";
    private static final long START_TIMEOUT_MS = 15_000L;

    private Process process;
    private java.io.Writer stdin;
    private java.io.BufferedReader stdout;
    private java.io.BufferedReader stderr;
    private Thread stderrDrain;

    /**
     * Start the background PowerShell process that hosts the GSMTC manager.
     *
     * @return {@code true} if the process started and signalled readiness
     */
    synchronized boolean start() {
        try {
            // Encode the init script as base64 (UTF-16LE) and pass it via
            // -EncodedCommand. The "-Command -" approach does NOT work when
            // launched from Java's ProcessBuilder because PowerShell does not
            // recognise the piped stdin as "redirected" for that flag and
            // exits immediately. -EncodedCommand avoids the issue entirely
            // and the stdin command loop ([Console]::In.ReadLine()) still
            // works fine for the get/prev/next/play/pause/exit commands.
            String encoded = java.util.Base64.getEncoder()
                    .encodeToString(INIT_SCRIPT.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-NonInteractive",
                    "-ExecutionPolicy", "Bypass",
                    "-EncodedCommand", encoded
            );
            pb.redirectErrorStream(false);
            process = pb.start();
            // IMPORTANT: explicitly use UTF-8 here rather than relying on
            // the JVM's platform-default charset. On Windows that default
            // is frequently the system's legacy codepage (e.g. Cp1252 /
            // Cp932), which cannot round-trip arbitrary Unicode track
            // metadata and silently turns it into '?'. This must match the
            // UTF8Encoding configured on the PowerShell side above.
            stdin = new java.io.OutputStreamWriter(process.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8);
            stdout = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            stderr = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getErrorStream(), java.nio.charset.StandardCharsets.UTF_8));

            // Drain stderr in the background so it can't deadlock the process
            // and so we can log PowerShell compilation errors.
            stderrDrain = new Thread(this::drainStderr, "Vinyl-PowerShell-Stderr");
            stderrDrain.setDaemon(true);
            stderrDrain.start();

            // Wait for the READY marker with a timeout. The WinRT type
            // compilation can take several seconds on first run.
            long deadline = System.currentTimeMillis() + START_TIMEOUT_MS;
            String line;
            while (System.currentTimeMillis() < deadline) {
                if (!process.isAlive()) {
                    System.err.println("[Vinyl] PowerShell host exited during init");
                    stop();
                    return false;
                }
                if (stdout.ready()) {
                    line = stdout.readLine();
                } else {
                    Thread.sleep(50);
                    continue;
                }
                if (line == null) {
                    System.err.println("[Vinyl] PowerShell stdout closed during init");
                    stop();
                    return false;
                }
                if (line.trim().equals(READY_MARKER)) {
                    System.out.println("[Vinyl] GSMTC PowerShell host ready");
                    return true;
                }
                // Ignore any other output during init.
            }
            System.err.println("[Vinyl] GSMTC PowerShell host timed out waiting for READY");
            stop();
            return false;
        } catch (Exception e) {
            System.err.println("[Vinyl] Failed to start GSMTC PowerShell host: " + e.getMessage());
            stop();
            return false;
        }
    }

    private void drainStderr() {
        try {
            String line;
            while (stderr != null && (line = stderr.readLine()) != null) {
                if (!line.isBlank()) {
                    System.err.println("[Vinyl/PS] " + line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Query the current media session.
     *
     * @return parsed metadata, or {@code null} on communication failure
     */
    synchronized MediaMetadata query() {
        if (!isAlive()) {
            return null;
        }
        try {
            stdin.write("get\n");
            stdin.flush();
            String line = stdout.readLine();
            if (line == null) {
                System.err.println("[Vinyl] PowerShell stdout closed during query");
                return null;
            }
            MediaMetadata result = parseJson(line);
            if (result == null) {
                System.err.println("[Vinyl] Failed to parse PowerShell response: " + line);
            }
            return result;
        } catch (Exception e) {
            System.err.println("[Vinyl] Query failed: " + e.getMessage());
            return null;
        }
    }

    synchronized void sendPrevious() {
        send("prev");
    }

    synchronized void sendNext() {
        send("next");
    }

    synchronized void sendPlay() {
        send("play");
    }

    synchronized void sendPause() {
        send("pause");
    }

    private void send(String cmd) {
        if (!isAlive()) {
            return;
        }
        try {
            stdin.write(cmd + "\n");
            stdin.flush();
        } catch (Exception ignored) {
        }
    }

    synchronized void stop() {
        try {
            if (stdin != null) {
                stdin.write("exit\n");
                stdin.flush();
            }
        } catch (Exception ignored) {
        }
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
        stdin = null;
        stdout = null;
        stderr = null;
    }

    synchronized boolean isAlive() {
        return process != null && process.isAlive() && stdin != null && stdout != null;
    }

    /**
     * Minimal JSON parser for the flat object returned by the PowerShell
     * script. We avoid pulling in a full JSON library to keep the mod light.
     */
    private static MediaMetadata parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<String, String> map = parseFlatJson(json);
        String stateStr = map.getOrDefault("state", "UNKNOWN");
        MediaMetadata.PlaybackState state;
        try {
            state = MediaMetadata.PlaybackState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            state = MediaMetadata.PlaybackState.UNKNOWN;
        }
        return new MediaMetadata(
                map.getOrDefault("title", ""),
                map.getOrDefault("artist", ""),
                map.getOrDefault("album", ""),
                state,
                map.getOrDefault("source", "")
        );
    }

    /**
     * Parse a flat JSON object (string keys, string values, no nesting)
     * into a map. Handles escaped quotes and backslashes.
     */
    private static Map<String, String> parseFlatJson(String json) {
        Map<String, String> result = new HashMap<>();
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }
        json = json.substring(1, json.length() - 1).trim();
        int i = 0;
        while (i < json.length()) {
            // skip whitespace and commas
            while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) {
                i++;
            }
            if (i >= json.length()) break;
            // parse key
            if (json.charAt(i) != '"') break;
            StringBuilder key = new StringBuilder();
            i++; // skip opening quote
            while (i < json.length() && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                    key.append(json.charAt(i + 1));
                    i += 2;
                } else {
                    key.append(json.charAt(i));
                    i++;
                }
            }
            i++; // skip closing quote
            // skip colon and whitespace
            while (i < json.length() && (json.charAt(i) == ':' || Character.isWhitespace(json.charAt(i)))) {
                i++;
            }
            // parse value
            if (i >= json.length() || json.charAt(i) != '"') break;
            StringBuilder value = new StringBuilder();
            i++; // skip opening quote
            while (i < json.length() && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\' && i + 1 < json.length()) {
                    value.append(json.charAt(i + 1));
                    i += 2;
                } else {
                    value.append(json.charAt(i));
                    i++;
                }
            }
            i++; // skip closing quote
            result.put(key.toString(), value.toString());
        }
        return result;
    }
}