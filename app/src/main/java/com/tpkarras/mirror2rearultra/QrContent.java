package com.tpkarras.mirror2rearultra;

import androidx.annotation.Nullable;

import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

import java.util.EnumMap;
import java.util.Map;

/** What the QR widget encodes, and the code itself. */
final class QrContent {
    /**
     * The text a phone's camera reads as "join this network".
     *
     * <p>No password means an open network. The characters that separate the
     * fields are escaped where they appear inside one, or a name with a
     * semicolon in it would cut the code short.
     */
    static String wifi(String ssid, String password) {
        StringBuilder text = new StringBuilder("WIFI:T:")
                .append(password.isEmpty() ? "nopass" : "WPA")
                .append(";S:").append(escape(ssid)).append(';');
        if (!password.isEmpty()) {
            text.append("P:").append(escape(password)).append(';');
        }
        return text.append(';').toString();
    }

    static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == ';' || character == ','
                    || character == ':' || character == '"') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    /**
     * The code's modules, true for dark, or null for nothing to encode or
     * more than a QR code holds.
     *
     * <p>The lowest error correction: the panel is a clean screen, not a
     * printed label that gets scuffed, and every level up makes the code
     * denser on a panel with few pixels to spare for each module.
     */
    @Nullable
    static boolean[][] encode(String text) {
        if (text.isEmpty()) return null;
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        try {
            ByteMatrix matrix = Encoder.encode(text, ErrorCorrectionLevel.L, hints).getMatrix();
            boolean[][] modules = new boolean[matrix.getHeight()][matrix.getWidth()];
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    modules[y][x] = matrix.get(x, y) == 1;
                }
            }
            return modules;
        } catch (WriterException | IllegalArgumentException tooLong) {
            return null;
        }
    }

    private QrContent() {}
}
