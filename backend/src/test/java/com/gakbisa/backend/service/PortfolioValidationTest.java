package com.gakbisa.backend.service;

import com.gakbisa.backend.config.StorageProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Skenario uji §8.2 no.3 (bagian validasi): nama aman, tipe, dan komparabilitas deklarasi. */
class PortfolioValidationTest {

    @Test
    void namaFileDiubahMenjadiAman() {
        assertEquals("laporan_ujian.pdf", PortfolioService.safeFileName("laporan ujian.pdf"));
        assertEquals("name__.png", PortfolioService.safeFileName("../../evil/../name !.png"));
        assertEquals("file", PortfolioService.safeFileName(null));
        String longName = "a".repeat(150) + ".pdf";
        assertTrue(PortfolioService.safeFileName(longName).length() <= 100);
    }

    @Test
    void deklarasiTipeHarusSekategori() {
        assertTrue(PortfolioService.compatible("application/pdf", "application/pdf"));
        assertTrue(PortfolioService.compatible("image/jpeg", "image/pjpeg"));
        assertFalse(PortfolioService.compatible("image/png", "application/pdf"),
            "PDF menyamar sebagai PNG harus ditolak");
    }

    @Test
    void propertiesMenjadiTahuBelumTerkonfigurasi() {
        StorageProperties p = new StorageProperties("", "", "bucket", 10L, java.util.List.of("image/png"));
        assertFalse(p.configured());
    }
}
