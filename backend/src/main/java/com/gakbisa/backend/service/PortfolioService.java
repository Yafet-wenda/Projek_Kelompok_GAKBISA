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
import java.util.Set;
import java.util.UUID;

/**
 * Metadata portofolio di PostgreSQL; isi file di Supabase Storage (proposal §6.1).
 * Validasi: ekstensi, MIME dari isi file, deklarasi MIME, dan ukuran maksimum.
 */
@Service
public class PortfolioService {

    private static final long PRD_MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> PRD_ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "application/pdf");

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
        String safeName = safeFileName(originalName);
        long maxFileSize = Math.min(props.maxFileSizeBytes(), PRD_MAX_FILE_SIZE);
        if (content.length > maxFileSize) {
            throw new ValidationException("Ukuran file melebihi batas 10 MB");
        }

        // Tipe file disimpulkan dari magic bytes isi file (bukan hanya ekstensi/deklarasi, §6.5).
        String byContent = MagicType.detect(content);
        String declared = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT);
        if (byContent.isEmpty()) {
            throw new ValidationException("Isi file tidak dikenali sebagai JPG/PNG/WEBP/PDF yang sah");
        }
        String extensionType = mimeFromExtension(safeName);
        if (!byContent.equalsIgnoreCase(extensionType)) {
            throw new ValidationException("Ekstensi file tidak sesuai dengan isi file");
        }
        if (!PRD_ALLOWED_TYPES.contains(byContent.toLowerCase(Locale.ROOT))
            || props.allowedMimeTypes().stream().noneMatch(t -> t.equalsIgnoreCase(byContent))) {
            throw new ValidationException("Tipe file tidak diizinkan: " + byContent
                + ". Yang diizinkan: JPG, PNG, WEBP, PDF");
        }
        if (!declared.isEmpty() && !compatible(byContent, declared)) {
            throw new ValidationException("Deklarasi tipe file tidak sesuai dengan isinya");
        }
        String detected = byContent;

        Portfolio p = new Portfolio();
        p.setUser(owner);
        p.setTitle(meta.title().trim());
        p.setDescription(meta.description());
        p.setFileName(safeName);
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

    /** Bucket bersifat privat; semua pratinjau memakai signed URL berumur 10 menit. */
    @Transactional(readOnly = true)
    public String accessUrl(UUID viewerId, UUID portfolioId) {
        Portfolio p = requireVisible(viewerId, portfolioId);
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

    /** Karya hanya untuk pemiliknya (atau admin) — dipakai halaman edit, bukan untuk penonton. */
    @Transactional(readOnly = true)
    public Portfolio requireOwned(UUID actorId, UUID portfolioId) {
        Portfolio p = portfolios.findById(portfolioId).orElseThrow(() -> new NotFoundException("Portofolio tidak ditemukan"));
        if (!p.getUser().getId().equals(actorId) && !isAdmin(actorId)) {
            throw new ForbiddenException("Karya ini bukan milikmu");
        }
        return p;
    }

    /**
     * Mengubah metadata karya saja. Isi berkas tidak disentuh — menggantinya berarti
     * mengunggah portofolio baru, karena path objek di storage ikut nama file lama.
     */
    @Transactional
    public Portfolio update(UUID actorId, UUID portfolioId, String title, String description,
                            PortfolioVisibility visibility) {
        Portfolio p = requireOwned(actorId, portfolioId);
        if (title == null || title.isBlank()) {
            throw new ValidationException("Judul portofolio wajib diisi");
        }
        String bersih = title.trim();
        if (bersih.length() > 150) {
            throw new ValidationException("Judul portofolio maksimal 150 karakter");
        }
        p.setTitle(bersih);
        p.setDescription(description == null || description.isBlank() ? null : description.trim());
        if (visibility != null) {
            p.setVisibility(visibility);
        }
        return portfolios.save(p);
    }

    @Transactional
    public void delete(UUID actorId, UUID portfolioId) {
        Portfolio p = portfolios.findById(portfolioId).orElseThrow(() -> new NotFoundException("Portofolio tidak ditemukan"));
        if (!p.getUser().getId().equals(actorId) && !isAdmin(actorId)) {
            throw new ForbiddenException("Hanya pemilik atau admin yang dapat menghapus karya");
        }
        // Jika Storage gagal, exception membatalkan transaksi agar metadata tetap tersedia untuk dicoba lagi.
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

    static String mimeFromExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new ValidationException("Nama file wajib memiliki ekstensi yang diizinkan");
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new ValidationException("File harus berekstensi JPG, JPEG, PNG, WEBP, atau PDF");
        }
        return switch (fileName.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "pdf" -> "application/pdf";
            default -> throw new ValidationException("Ekstensi file tidak diizinkan");
        };
    }
}
