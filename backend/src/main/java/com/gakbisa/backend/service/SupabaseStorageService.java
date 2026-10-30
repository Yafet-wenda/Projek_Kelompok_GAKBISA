package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.config.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Klien Supabase Storage (REST API) untuk bucket portofolio.
 * Service key hanya hidup di backend — tidak pernah dikirim ke browser (proposal §4.6).
 */
@Service
public class SupabaseStorageService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseStorageService.class);

    public record UploadResult(String publicUrl) {}
    public record FileContent(byte[] bytes, String contentType) {}

    private final StorageProperties props;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    public SupabaseStorageService(StorageProperties props) {
        this.props = props;
    }

    private void requireConfig() {
        if (!props.configured()) {
            // Pesan yang dapat dipahami, tanpa menghentikan data barter (kebutuhan nonfungsional §4.6)
            throw new ValidationException("Ruang penyimpanan berkas belum aktif di server ini.");
        }
    }

    /** Unggah file ke path {userId}/{portfolioId}/{namaAman}. */
    public void upload(UUID userId, UUID portfolioId, String safeFileName, byte[] content, String contentType) {
        requireConfig();
        String path = userId + "/" + portfolioId + "/" + safeFileName;
        String boundary = "----gakbisa" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, safeFileName, contentType, content);

        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl("/object/" + props.bucket() + "/" + path)))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + props.serviceKey())
            .header("apikey", props.serviceKey())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        send(req, 200, "Upload ke penyimpanan gagal");
    }

    public FileContent download(UUID userId, UUID portfolioId, String safeFileName) {
        requireConfig();
        String path = userId + "/" + portfolioId + "/" + safeFileName;
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl("/object/" + props.bucket() + "/" + path)))
            .timeout(Duration.ofSeconds(60))
            .header("Authorization", "Bearer " + props.serviceKey())
            .header("apikey", props.serviceKey())
            .GET()
            .build();
        try {
            HttpResponse<byte[]> res = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new ValidationException("File tidak dapat diambil dari penyimpanan");
            }
            String ct = res.headers().firstValue("content-type").orElse("application/octet-stream");
            return new FileContent(res.body(), ct);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("Pengambilan file terganggu");
        } catch (IOException e) {
            log.warn("Gagal mengambil file {}: {}", path, e.toString());
            throw new ValidationException("Layanan penyimpanan sedang tidak tersedia");
        }
    }

    public void delete(String path) {
        if (!props.configured()) return; // tidak ada yang bisa dibersihkan
        HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl("/object/" + props.bucket() + "/" + path)))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + props.serviceKey())
            .header("apikey", props.serviceKey())
            .DELETE()
            .build();
        try {
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // File yatim dicatat agar bisa dibersihkan manual; jangan gagalkan alur utama.
            log.warn("Gagal menghapus objek yatim {}: {}", path, e.toString());
        }
    }

    /** Signed URL berumur terbatas untuk file privat (proposal §6.5). */
    public String createSignedUrl(String path, int expiresInSec) {
        requireConfig();
        String json = "{\"expiresIn\":" + expiresInSec + "}";
        HttpRequest req = HttpRequest.newBuilder(
                URI.create(apiUrl("/object/sign/" + props.bucket() + "/" + URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20"))))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + props.serviceKey())
            .header("apikey", props.serviceKey())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> res = send(req, 200, "Gagal membuat tautan sementara");
        // Respons: {"signedURL":"object/..."} — ekstrak sederhana tanpa dependensi JSON.
        int idx = res.body().indexOf("\"signedURL\"");
        if (idx < 0) throw new ValidationException("Respons penyimpanan tidak dapat dibaca");
        int start = res.body().indexOf(':', idx) + 2;
        int end = res.body().indexOf('"', start);
        String relative = res.body().substring(start, end).replace("\\/", "/");
        String base = props.baseUrl().replaceAll("/+$", "");
        return relative.startsWith("http") ? relative : base + "/" + relative;
    }

    private String apiUrl(String suffix) {
        return props.baseUrl().replaceAll("/+$", "") + "/storage/v1" + suffix;
    }

    private HttpResponse<String> send(HttpRequest req, int expected, String failMessage) {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != expected) {
                log.warn("Supabase Storage {} {} -> {}", req.method(), req.uri(), res.statusCode());
                throw new ValidationException(failMessage);
            }
            return res;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException(failMessage);
        } catch (IOException e) {
            log.warn("Koneksi penyimpanan gagal: {}", e.toString());
            throw new ValidationException("Layanan penyimpanan sedang tidak tersedia");
        }
    }

    private static byte[] multipartBody(String boundary, String fileName, String contentType, byte[] content) {
        String head = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
            + "Content-Type: " + contentType + "\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";
        byte[] headB = head.getBytes(StandardCharsets.UTF_8);
        byte[] tailB = tail.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headB.length + content.length + tailB.length];
        System.arraycopy(headB, 0, out, 0, headB.length);
        System.arraycopy(content, 0, out, headB.length, content.length);
        System.arraycopy(tailB, 0, out, headB.length + content.length, tailB.length);
        return out;
    }
}
