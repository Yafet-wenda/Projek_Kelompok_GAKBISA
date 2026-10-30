package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.config.AppExceptions.NotFoundException;
import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.config.StorageProperties;
import com.gakbisa.backend.domain.Portfolio;
import com.gakbisa.backend.domain.PortfolioVisibility;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.repository.PortfolioRepository;
import com.gakbisa.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Metadata portofolio di PostgreSQL; isi file di Supabase Storage (proposal §6.1).
 * Validasi: ekstensi + MIME (dibaca dari isi file, bukan hanya ekstensi) + ukuran maksimum.
 */
@Service
public class PortfolioService {

    private final PortfolioRepository portfolios;
    private final UserRepository users;
    private final SupabaseStorageService storage;
    private final StorageProperties props;

    public PortfolioService(PortfolioRepository portfolios, UserRepository users,
                            SupabaseStorageService storage, StorageProperties props) {
        this.portfolios = portfolios;
        this.users = users;
        this.storage = storage;
        this.props = props;
    }

    public record NewPortfolio(String title, String description, PortfolioVisibility visibility) {}

    @Transactional
    public Portfolio upload(UUID ownerId, NewPortfolio meta, String originalName, String declaredType, byte[] content) {
        User owner = users.findById(ownerId).orElseThrow(() -> new NotFoundException("Pengguna tidak ditemukan"));
        if (meta.title() == null || meta.title().isBlank()) {
            throw new ValidationException("Judul portofolio wajib diisi");
        }
        if (content == null || content.length == 0) {
            throw new ValidationException("File kosong");
        }
        if (content.length > props.maxFileSizeBytes()) {
            throw new ValidationException("Ukuran file melebihi batas " + (props.maxFileSizeBytes() / 1024 / 1024) + " MB");
        }

        // Tipe file disimpulkan dari magic bytes isi file (bukan hanya ekstensi/deklarasi, §6.5).
        String byContent = MagicType.detect(content);
        String declared = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT);
        if (byContent.isEmpty()) {
            throw new ValidationException("Isi file tidak dikenali sebagai JPG/PNG/WEBP/PDF yang sah");
        }
        if (props.allowedMimeTypes().stream().noneMatch(t -> t.equalsIgnoreCase(byContent))) {
            throw new ValidationException("Tipe file tidak diizinkan: " + byContent
                + ". Yang diizinkan: " + String.join(", ", props.allowedMimeTypes()));
        }
        if (!declared.isEmpty() && !compatible(byContent, declared)) {
            throw new ValidationException("Deklarasi tipe file tidak sesuai dengan isinya");
        }
        String detected = byContent;

        Portfolio p = new Portfolio();
        p.setUser(owner);
        p.setTitle(meta.title().trim());
        p.setDescription(meta.description());
        p.setFileName(safeFileName(originalName));
        p.setMimeType(detected);
        p.setFileSize(content.length);
        p.setVisibility(meta.visibility() == null ? PortfolioVisibility.PUBLIC : meta.visibility());
        p.setFilePath(owner.getId() + "/" + UUID.randomUUID() + "/pending"); // placeholder

        Portfolio saved = portfolios.save(p);
        String path = owner.getId() + "/" + saved.getId() + "/" + saved.getFileName();
        try {
            storage.upload(owner.getId(), saved.getId(), saved.getFileName(), content, detected);
            saved.setFilePath(path);
            return portfolios.save(saved);
        } catch (RuntimeException e) {
            // Upload gagal: hapus metadata agar tidak ada baris yatim, lalu sampaikan kendala.
            portfolios.delete(saved);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Portfolio requireVisible(UUID viewerId, UUID portfolioId) {
        Portfolio p = portfolios.findById(portfolioId).orElseThrow(() -> new NotFoundException("Portofolio tidak ditemukan"));
        boolean owner = p.getUser().getId().equals(viewerId);
        if (p.getVisibility() == PortfolioVisibility.PRIVATE && !owner && !isAdmin(viewerId)) {
            throw new ForbiddenException("Karya ini bersifat privat");
        }
        return p;
    }

    /** Tautan akses untuk penampil: publik langsung, privat lewat signed URL 10 menit. */
    @Transactional(readOnly = true)
    public String accessUrl(UUID viewerId, UUID portfolioId) {
        Portfolio p = requireVisible(viewerId, portfolioId);
        if (p.getVisibility() == PortfolioVisibility.PUBLIC) {
            return p.getFilePath(); // frontend dapat membentuk URL publik bucket bila bucket dibuat publik
        }
        return storage.createSignedUrl(p.getFilePath(), 600);
    }

    @Transactional(readOnly = true)
    public SupabaseStorageService.FileContent content(UUID viewerId, UUID portfolioId) {
        Portfolio p = requireVisible(viewerId, portfolioId);
        return storage.download(p.getUser().getId(), p.getId(), p.getFileName());
    }

    @Transactional(readOnly = true)
    public Page<Portfolio> publicGallery(Pageable pageable) {
        return portfolios.findByVisibility(PortfolioVisibility.PUBLIC, pageable);
    }

    @Transactional(readOnly = true)
    public List<Portfolio> forUser(UUID userId) {
        return portfolios.findByUserId(userId);
    }

    @Transactional
    public void delete(UUID actorId, UUID portfolioId) {
        Portfolio p = portfolios.findById(portfolioId).orElseThrow(() -> new NotFoundException("Portofolio tidak ditemukan"));
        if (!p.getUser().getId().equals(actorId) && !isAdmin(actorId)) {
            throw new ForbiddenException("Hanya pemilik atau admin yang dapat menghapus karya");
        }
        // Hapus metadata lebih dulu; kegagalan hapus objek hanya dicatat (file yatim dibersihkan nanti).
        portfolios.delete(p);
        if (p.getFilePath() != null && !p.getFilePath().endsWith("/pending")) {
            storage.delete(p.getFilePath());
        }
    }

    private boolean isAdmin(UUID userId) {
        return users.findById(userId).map(u -> u.getRole() == UserRole.ADMIN).orElse(false);
    }

    /** Nama file aman: hanya alfanumerik, dot, dash, underscore; maks 100 karakter. */
    static String safeFileName(String raw) {
        if (raw == null || raw.isBlank()) return "file";
        String base = raw.replaceAll(".*[/\\\\]", "");
        String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (cleaned.isBlank() || cleaned.equals(".")) cleaned = "file";
        return cleaned.length() > 100 ? cleaned.substring(cleaned.length() - 100) : cleaned;
    }

    /** Tipe deklarasi browser harus sekategori dengan tipe hasil pemeriksaan (image/* vs application/pdf). */
    static boolean compatible(String expected, String declared) {
        if (expected.equalsIgnoreCase(declared)) return true;
        String e = expected.split("/")[0];
        String d = declared.split("/")[0];
        return e.equalsIgnoreCase(d);
    }
}
