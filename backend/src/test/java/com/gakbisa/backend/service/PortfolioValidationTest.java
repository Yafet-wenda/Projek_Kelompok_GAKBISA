package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.config.StorageProperties;
import com.gakbisa.backend.domain.Portfolio;
import com.gakbisa.backend.domain.PortfolioVisibility;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        StorageProperties p = new StorageProperties("", "", "bucket", 10L, List.of("image/png"));
        assertFalse(p.configured());
    }

    @ParameterizedTest
    @MethodSource("mediaHeaders")
    void magicBytesMengenaliTipeYangDidukung(String mimeType, byte[] header) {
        assertEquals(mimeType, MagicType.detect(header));
    }

    @Test
    void magicBytesMenolakTipeTidakDidukung() {
        assertEquals("", MagicType.detect(null));
        assertEquals("", MagicType.detect(new byte[12]));
        assertEquals("", MagicType.detect("GIF89a000000".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("", MagicType.detect("RIFF0000AVI ".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("", MagicType.detect("ftyp00000000".getBytes(StandardCharsets.US_ASCII)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"isom", "iso2", "iso3", "iso4", "iso5", "iso6", "iso7", "iso8", "iso9",
        "mp41", "mp42", "avc1", "M4V ", "MSNV"})
    void magicBytesMengenaliMajorBrandMp4(String brand) {
        assertEquals("video/mp4", MagicType.detect(mp4Header(brand)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"avif", "avis", "heic", "heix", "hevc", "hevx", "mif1", "msf1", "qt  ", "xxxx"})
    void magicBytesMenolakMajorBrandBukanMp4(String brand) {
        byte[] header = Arrays.copyOf(mp4Header(brand), 20);
        header[3] = 20;
        System.arraycopy("isom".getBytes(StandardCharsets.US_ASCII), 0, header, 16, 4);
        assertEquals("", MagicType.detect(header));
    }

    @ParameterizedTest
    @ValueSource(strings = {"video/mp4", "video/webm"})
    void magicBytesMenolakHeaderVideoTerpotong(String mimeType) {
        byte[] header = videoHeader(mimeType);
        for (int length = 0; length < header.length; length++) {
            assertEquals("", MagicType.detect(Arrays.copyOf(header, length)), "Panjang: " + length);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8})
    void magicBytesMengenaliPanjangHeaderEbmlVariabel(int sizeLength) {
        byte[] original = videoHeader("video/webm");
        byte[] header = new byte[original.length + sizeLength - 1];
        System.arraycopy(original, 0, header, 0, 4);
        header[4] = (byte) (1 << (8 - sizeLength));
        header[3 + sizeLength] |= 8;
        System.arraycopy(original, 5, header, 4 + sizeLength, original.length - 5);
        assertEquals("video/webm", MagicType.detect(header));
    }

    @Test
    void magicBytesMengenaliDocTypeDiAntaraElemenEbml() {
        byte[] header = ebmlHeader(0x42, 0x86, 0x81, 1,
            0x42, 0x82, 0x84, 'w', 'e', 'b', 'm', 0xEC, 0x80);
        assertEquals("video/webm", MagicType.detect(header));
        byte[] withPayload = Arrays.copyOf(header, header.length + 3);
        assertEquals("video/webm", MagicType.detect(withPayload));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 0xFF, 0x80, 0x87, 0x89, 0x01})
    void magicBytesMenolakUkuranHeaderEbmlTidakSah(int size) {
        byte[] header = videoHeader("video/webm");
        header[4] = (byte) size;
        assertEquals("", MagicType.detect(header));
    }

    @ParameterizedTest
    @MethodSource("invalidEbmlHeaders")
    void magicBytesMenolakEbmlBukanWebmAtauRusak(String scenario, byte[] header) {
        assertEquals("", MagicType.detect(header), scenario);
    }

    @ParameterizedTest
    @ValueSource(strings = {"video/mp4", "video/webm"})
    void unggahVideoPadaBatasUkuranDiterima(String mimeType) {
        byte[] content = videoHeader(mimeType);
        PortfolioService service = service(content.length, List.of("video/mp4", "video/webm"));
        when(portfolios.save(any(Portfolio.class))).thenAnswer(invocation -> {
            Portfolio p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        Portfolio saved = service.upload(ownerId, meta, "video", mimeType, content);

        assertEquals(mimeType, saved.getMimeType());
        assertEquals(content.length, saved.getFileSize());
        assertEquals(ownerId + "/" + saved.getId() + "/video", saved.getFilePath());
        verify(storage).upload(ownerId, saved.getId(), "video", content, mimeType);
        verify(portfolios, times(2)).save(saved);
    }

    @ParameterizedTest
    @ValueSource(strings = {"video/mp4", "video/webm"})
    void unggahVideoMelebihiBatasUkuranDitolak(String mimeType) {
        byte[] content = videoHeader(mimeType);
        PortfolioService service = service(content.length - 1, List.of(mimeType));

        ValidationException error = assertThrows(ValidationException.class,
            () -> service.upload(ownerId, meta, "video", mimeType, content));

        assertTrue(error.getMessage().contains("Ukuran file melebihi batas"));
        verifyNoInteractions(portfolios, storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"video/mp4", "video/webm"})
    void unggahVideoDiLuarWhitelistDitolak(String mimeType) {
        PortfolioService service = service(1024, List.of("image/png", "application/pdf"));

        ValidationException error = assertThrows(ValidationException.class,
            () -> service.upload(ownerId, meta, "video", mimeType, videoHeader(mimeType)));

        assertTrue(error.getMessage().contains("Tipe file tidak diizinkan: " + mimeType));
        verifyNoInteractions(portfolios, storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"video/mp4", "video/webm"})
    void unggahVideoDenganDeklarasiGambarDitolak(String mimeType) {
        PortfolioService service = service(1024, List.of(mimeType));

        ValidationException error = assertThrows(ValidationException.class,
            () -> service.upload(ownerId, meta, "gambar.png", "image/png", videoHeader(mimeType)));

        assertEquals("Deklarasi tipe file tidak sesuai dengan isinya", error.getMessage());
        verifyNoInteractions(portfolios, storage);
    }

    @Test
    void unggahIsiTidakDikenaliDitolakMeskiDeklarasiVideo() {
        PortfolioService service = service(1024, List.of("video/mp4"));

        ValidationException error = assertThrows(ValidationException.class,
            () -> service.upload(ownerId, meta, "video.mp4", "video/mp4", new byte[12]));

        assertEquals("Isi file tidak dikenali sebagai JPG/PNG/WEBP/MP4/WEBM/PDF yang sah", error.getMessage());
        verifyNoInteractions(portfolios, storage);
    }

    private final UUID ownerId = UUID.randomUUID();
    private final PortfolioService.NewPortfolio meta =
        new PortfolioService.NewPortfolio("Video", null, PortfolioVisibility.PUBLIC);
    private PortfolioRepository portfolios;
    private SupabaseStorageService storage;

    private PortfolioService service(long maxFileSize, List<String> allowedTypes) {
        portfolios = mock(PortfolioRepository.class);
        storage = mock(SupabaseStorageService.class);
        UserRepository users = mock(UserRepository.class);
        User owner = new User();
        owner.setId(ownerId);
        when(users.findById(ownerId)).thenReturn(Optional.of(owner));
        return new PortfolioService(portfolios, users, storage,
            new StorageProperties("", "", "portfolio-files", maxFileSize, allowedTypes));
    }

    private static Stream<Arguments> mediaHeaders() {
        return Stream.of(
            Arguments.of("application/pdf", "%PDF-1.70000".getBytes(StandardCharsets.US_ASCII)),
            Arguments.of("image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0, 0, 0, 0, 0, 0, 0, 0}),
            Arguments.of("image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0, 0, 0, 0}),
            Arguments.of("image/webp", "RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)),
            Arguments.of("video/mp4", videoHeader("video/mp4")),
            Arguments.of("video/webm", videoHeader("video/webm"))
        );
    }

    private static Stream<Arguments> invalidEbmlHeaders() {
        return Stream.of(
            Arguments.of("Matroska", ebmlHeader(0x42, 0x82, 0x88, 'm', 'a', 't', 'r', 'o', 's', 'k', 'a')),
            Arguments.of("DocType absent", ebmlHeader(0x42, 0x83, 0x84, 'w', 'e', 'b', 'm')),
            Arguments.of("DocType not exact", ebmlHeader(0x42, 0x82, 0x85, 'w', 'e', 'b', 'm', 0)),
            Arguments.of("DocType uppercase", ebmlHeader(0x42, 0x82, 0x84, 'W', 'E', 'B', 'M')),
            Arguments.of("DocType inside unrelated payload", ebmlHeader(0xEC, 0x87,
                0x42, 0x82, 0x84, 'w', 'e', 'b', 'm')),
            Arguments.of("DocType payload truncated", ebmlHeader(0x42, 0x82, 0x88, 'w', 'e', 'b', 'm')),
            Arguments.of("Duplicate DocType", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm',
                0x42, 0x82, 0x84, 'w', 'e', 'b', 'm')),
            Arguments.of("Truncated element ID", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm', 0x42)),
            Arguments.of("Zero element ID", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm', 0, 0x80)),
            Arguments.of("Reserved element ID", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm', 0xFF, 0x80)),
            Arguments.of("Element ID too long", ebmlHeader(0x08, 0, 0, 0, 1, 0x80,
                0x42, 0x82, 0x84, 'w', 'e', 'b', 'm')),
            Arguments.of("Truncated element size", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm', 0xEC, 0x40)),
            Arguments.of("Zero size marker", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm', 0xEC, 0)),
            Arguments.of("Unknown element size", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm', 0xEC, 0xFF)),
            Arguments.of("Child exceeds header", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm', 0xEC, 0x81)),
            Arguments.of("Large element size", ebmlHeader(0x42, 0x82, 0x84, 'w', 'e', 'b', 'm',
                0xEC, 0x01, 0x01, 0, 0, 0, 0, 0, 0))
        );
    }

    private static byte[] videoHeader(String mimeType) {
        return switch (mimeType) {
            case "video/mp4" -> mp4Header("isom");
            case "video/webm" -> ebmlHeader(0x42, 0x82, 0x40, 0x04, 'w', 'e', 'b', 'm');
            default -> throw new IllegalArgumentException(mimeType);
        };
    }

    private static byte[] mp4Header(String brand) {
        byte[] header = new byte[] {0, 0, 0, 16, 'f', 't', 'y', 'p', 0, 0, 0, 0, 0, 0, 0, 0};
        System.arraycopy(brand.getBytes(StandardCharsets.US_ASCII), 0, header, 8, 4);
        return header;
    }

    private static byte[] ebmlHeader(int... payload) {
        byte[] header = Arrays.copyOf(new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3,
            (byte) (0x80 | payload.length)}, 5 + payload.length);
        for (int i = 0; i < payload.length; i++) header[5 + i] = (byte) payload[i];
        return header;
    }
}
