package com.gakbisa.backend.service;

/**
 * Deteksi tipe media dari magic bytes isi file — jangan percaya ekstensi/deklarasi saja
 * (proposal §6.5). Mendukung tipe yang diizinkan tahap awal: JPG, PNG, WEBP, PDF.
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
        return "";
    }
}
