package com.echostep.app.net;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Small Edge Read Aloud client. It uses Microsoft's public Edge TTS
 * WebSocket endpoint (no API key) and returns one MP3 file.
 */
public final class EdgeTtsClient {
    private static final String TRUSTED = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String VERSION = "2-143.0.3650.75";
    private static final String VOICE = "en-US-AvaMultilingualNeural";
    private static final int MAX_CHUNK_BYTES = 3600;

    public File synthesize(String text, File output, int ratePercent) throws Exception {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("没有可朗读的文本");
        }
        List<String> chunks = splitText(text);
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        for (String chunk : chunks) {
            all.write(synthesizeChunk(chunk, ratePercent));
        }
        File parent = output.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        Files.write(output.toPath(), all.toByteArray());
        return output;
    }

    private byte[] synthesizeChunk(String text, int ratePercent) throws Exception {
        String connectionId = UUID.randomUUID().toString().replace("-", "");
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1"
                + "?TrustedClientToken=" + TRUSTED
                + "&ConnectionId=" + connectionId
                + "&Sec-MS-GEC=" + EdgeToken.generate()
                + "&Sec-MS-GEC-Version=" + VERSION;

        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        WebSocket socket = new WebSocketFactory()
                .setConnectionTimeout(15000)
                .createSocket(url)
                .addHeader("Pragma", "no-cache")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 "
                        + "Edg/143.0.0.0")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Cookie", "muid=" + randomMuid() + ";")
                .addListener(new WebSocketAdapter() {
                    @Override
                    public void onConnected(WebSocket websocket, java.util.Map<String, List<String>> headers) {
                        try {
                            websocket.sendText(configMessage());
                            websocket.sendText(ssmlMessage(requestId, text, ratePercent));
                        } catch (Exception e) {
                            failure.compareAndSet(null, e);
                            done.countDown();
                        }
                    }

                    @Override
                    public void onTextMessage(WebSocket websocket, String message) {
                        try {
                            int split = message.indexOf("\r\n\r\n");
                            String head = split >= 0 ? message.substring(0, split) : message;
                            if (head.contains("Path:turn.end")) {
                                done.countDown();
                            }
                        } catch (Exception e) {
                            failure.compareAndSet(null, e);
                            done.countDown();
                        }
                    }

                    @Override
                    public void onBinaryMessage(WebSocket websocket, byte[] binary) {
                        try {
                            if (binary.length < 2) {
                                return;
                            }
                            int headerLength = ((binary[0] & 0xFF) << 8) | (binary[1] & 0xFF);
                            if (headerLength <= 2 || headerLength + 2 > binary.length) {
                                return;
                            }
                            String head = new String(binary, 2, headerLength - 2, StandardCharsets.UTF_8);
                            if (!head.contains("Path:audio")) {
                                return;
                            }
                            int payload = headerLength + 2;
                            if (payload < binary.length) {
                                audio.write(binary, payload, binary.length - payload);
                            }
                        } catch (Exception e) {
                            failure.compareAndSet(null, e);
                            done.countDown();
                        }
                    }

                    @Override
                    public void onError(WebSocket websocket, WebSocketException cause) {
                        failure.compareAndSet(null, cause);
                        done.countDown();
                    }

                    @Override
                    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame,
                                               WebSocketFrame clientCloseFrame, boolean closedByServer) {
                        done.countDown();
                    }
                })
                .connect();

        boolean finished = done.await(240, TimeUnit.SECONDS);
        try {
            socket.disconnect();
        } catch (Exception ignored) {
        }
        if (!finished) {
            throw new IOException("Edge TTS 连接超时");
        }
        if (failure.get() != null) {
            throw new IOException("Edge TTS 失败: " + failure.get().getMessage(), failure.get());
        }
        if (audio.size() == 0) {
            throw new IOException("Edge TTS 没有返回音频");
        }
        return audio.toByteArray();
    }

    private static String configMessage() {
        String timestamp = timestamp(false);
        return "X-Timestamp:" + timestamp + "\r\n"
                + "Content-Type:application/json; charset=utf-8\r\n"
                + "Path:speech.config\r\n\r\n"
                + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                + "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
    }

    private static String ssmlMessage(String requestId, String text, int ratePercent) {
        String rate = String.format(Locale.US, "%+d%%", Math.max(-50, Math.min(50, ratePercent)));
        String escaped = escapeXml(text);
        String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
                + "<voice name='" + VOICE + "'>"
                + "<prosody pitch='+0Hz' rate='" + rate + "' volume='+0%'>"
                + escaped
                + "</prosody></voice></speak>";
        return "X-RequestId:" + requestId + "\r\n"
                + "Content-Type:application/ssml+xml\r\n"
                + "X-Timestamp:" + timestamp(true) + "\r\n"
                + "Path:ssml\r\n\r\n"
                + ssml;
    }

    private static String timestamp(boolean withZ) {
        SimpleDateFormat format = new SimpleDateFormat(
                "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date()) + (withZ ? "Z" : "");
    }

    private static String escapeXml(String text) {
        StringBuilder out = new StringBuilder(text.length() + 32);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0 && c <= 8) || (c >= 11 && c <= 12) || (c >= 14 && c <= 31)) {
                out.append(' ');
            } else if (c == '&') {
                out.append("&amp;");
            } else if (c == '<') {
                out.append("&lt;");
            } else if (c == '>') {
                out.append("&gt;");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static List<String> splitText(String text) {
        List<String> chunks = new ArrayList<>();
        String remaining = text.trim();
        while (remaining.getBytes(StandardCharsets.UTF_8).length > MAX_CHUNK_BYTES) {
            int split = safeSplit(remaining, MAX_CHUNK_BYTES);
            if (split <= 0) {
                split = remaining.length();
            }
            String chunk = remaining.substring(0, split).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            remaining = remaining.substring(split).trim();
        }
        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }
        return chunks;
    }

    private static int safeSplit(String text, int maxBytes) {
        int bytes = 0;
        int bestNewline = -1;
        int bestSpace = -1;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + len > maxBytes) {
                break;
            }
            bytes += len;
            if (cp == '\n') {
                bestNewline = i + Character.charCount(cp);
            } else if (Character.isWhitespace(cp)) {
                bestSpace = i;
            }
            i += Character.charCount(cp);
        }
        if (bestNewline > 0) {
            return bestNewline;
        }
        if (bestSpace > 0) {
            return bestSpace;
        }
        // Avoid cutting a surrogate pair / multi-byte codepoint.
        int fallback = 0;
        int used = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (used + len > maxBytes) break;
            used += len;
            i += Character.charCount(cp);
            fallback = i;
        }
        return fallback;
    }

    private static String randomMuid() {
        StringBuilder builder = new StringBuilder(32);
        String hex = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        builder.append(hex);
        while (builder.length() < 32) builder.append('0');
        return builder.substring(0, 32);
    }
}
