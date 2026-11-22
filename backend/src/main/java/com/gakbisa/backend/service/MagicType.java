package com.gakbisa.backend.service;

import java.nio.charset.StandardCharsets;

/**
 * Deteksi tipe media dari magic bytes isi file — jangan percaya ekstensi/deklarasi saja
 * (proposal §6.5). Mendukung: JPG, PNG, WEBP, PDF, MP4, WEBM.
 */
final class MagicType {

    private MagicType() {}

    static String detect(byte[] content) {
        if (content == null || content.length < 12) return "";
        // PDF: %PDF
        if (content[0] == 0x25 && content[1] == 0x50 && content[2] == 0x44 && content[3] == 0x46) {
            return "application/pdf";
        }
        // PNG: 89 50 4E 47
        if ((content[0] & 0xFF) == 0x89 && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47) {
            return "image/png";
        }
        // JPEG: FF D8 FF
        if ((content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // WEBP: RIFF....WEBP
        if (content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
            && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        if (content.length >= 16 && content[4] == 'f' && content[5] == 't'
            && content[6] == 'y' && content[7] == 'p') {
            return switch (new String(content, 8, 4, StandardCharsets.US_ASCII)) {
                case "isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
                     "mp41", "mp42", "avc1", "M4V ", "MSNV" -> "video/mp4";
                default -> "";
            };
        }
        if ((content[0] & 0xFF) == 0x1A && content[1] == 0x45
            && (content[2] & 0xFF) == 0xDF && (content[3] & 0xFF) == 0xA3
            && hasWebmDocType(content)) {
            return "video/webm";
        }
        return "";
    }

    private static boolean hasWebmDocType(byte[] content) {
        EbmlVint headerSize = readVint(content, 4, content.length, false);
        if (headerSize == null || headerSize.value() > content.length - 4 - headerSize.length()) return false;
        int offset = 4 + headerSize.length();
        int end = offset + (int) headerSize.value();
        boolean found = false;
        while (offset < end) {
            EbmlVint id = readVint(content, offset, end, true);
            if (id == null) return false;
            offset += id.length();
            EbmlVint size = readVint(content, offset, end, false);
            if (size == null) return false;
            offset += size.length();
            if (size.value() > end - offset) return false;
            if (id.value() == 0x4282) {
                if (found || size.value() != 4 || content[offset] != 'w' || content[offset + 1] != 'e'
                    || content[offset + 2] != 'b' || content[offset + 3] != 'm') return false;
                found = true;
            }
            offset += (int) size.value();
        }
        return found;
    }

    private record EbmlVint(long value, int length) {}

    private static EbmlVint readVint(byte[] content, int offset, int end, boolean elementId) {
        if (offset >= end) return null;
        int first = content[offset] & 0xFF;
        int marker = 0x80;
        int length = 1;
        int maxLength = elementId ? 4 : 8;
        while (length <= maxLength && (first & marker) == 0) {
            marker >>>= 1;
            length++;
        }
        if (length > maxLength || length > end - offset) return null;
        long value = first & (marker - 1);
        for (int i = 1; i < length; i++) value = (value << 8) | (content[offset + i] & 0xFF);
        // Unknown sizes and reserved element IDs are not valid in this bounded header.
        if (value == (1L << (7 * length)) - 1 || (elementId && value == 0)) return null;
        return new EbmlVint(elementId ? value | (1L << (7 * length)) : value, length);
    }
}
