package com.gakbisa.backend.controller;

import com.gakbisa.backend.domain.Portfolio;
import com.gakbisa.backend.domain.PortfolioVisibility;
import com.gakbisa.backend.service.PortfolioService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolios;

    public PortfolioController(PortfolioService portfolios) {
        this.portfolios = portfolios;
    }

    public record PortfolioDto(UUID id, UUID ownerId, String title, String description,
                               String fileName, String mimeType, long fileSize,
                               String visibility, Object createdAt) {
        static PortfolioDto from(Portfolio p) {
            return new PortfolioDto(p.getId(), p.getUser().getId(), p.getTitle(), p.getDescription(),
                p.getFileName(), p.getMimeType(), p.getFileSize(), p.getVisibility().name(), p.getCreatedAt());
        }
    }

    /** Galeri publik untuk tamu (halaman katalog). */
    @GetMapping
    public Page<PortfolioDto> gallery(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "12") int size) {
        return portfolios.publicGallery(PageRequest.of(page, Math.min(size, 50))).map(PortfolioDto::from);
    }

    @GetMapping("/user/{userId}")
    public List<PortfolioDto> forUser(@PathVariable UUID userId) {
        return portfolios.forUser(userId).stream()
            .filter(p -> p.getVisibility() == PortfolioVisibility.PUBLIC
                || p.getUser().getId().equals(CurrentUser.id()) || CurrentUser.isAdmin())
            .map(PortfolioDto::from)
            .toList();
    }

    /**
     * Unggah multipart: file + judul + deskripsi + visibilitas.
     * Field non-file memakai @RequestParam karena browser mengirimnya sebagai bagian
     * text/plain; dengan @RequestPart Spring menuntut konverter pesan per bagian dan
     * membalas 415 untuk form HTML biasa.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PortfolioDto upload(@RequestPart("file") MultipartFile file,
                               @RequestParam("title") String title,
                               @RequestParam(value = "description", required = false) String description,
                               @RequestParam(value = "visibility", required = false) PortfolioVisibility visibility) throws IOException {
        Portfolio p = portfolios.upload(CurrentUser.id(),
            new PortfolioService.NewPortfolio(title, description, visibility),
            file.getOriginalFilename(), file.getContentType(), file.getBytes());
        return PortfolioDto.from(p);
    }

    @GetMapping("/{id}")
    public PortfolioDto view(@PathVariable UUID id) {
        return PortfolioDto.from(portfolios.requireVisible(CurrentUser.id(), id));
    }

    /** Aliran isi file dengan pemeriksaan hak akses di backend (untuk file privat). */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable UUID id) {
        var file = portfolios.content(CurrentUser.id(), id);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.contentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(new ByteArrayResource(file.bytes()));
    }

    /** Signed URL sementara untuk pratinjau cepat (10 menit). */
    @GetMapping("/{id}/access-url")
    public Map<String, String> accessUrl(@PathVariable UUID id) {
        return Map.of("url", portfolios.accessUrl(CurrentUser.id(), id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        portfolios.delete(CurrentUser.id(), id);
    }
}
