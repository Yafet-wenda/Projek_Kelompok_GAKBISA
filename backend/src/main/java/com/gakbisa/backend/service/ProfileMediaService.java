package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class ProfileMediaService {

    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;
    private static final long BANNER_MAX_BYTES = 10L * 1024 * 1024;

    private final UserRepository users;
    private final Path root;

    public ProfileMediaService(UserRepository users,
                               @Value("${gakbisa.profile-media.directory:uploads/profile-media}") String directory) {
        this.users = users;
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    @Transactional
    public User uploadAvatar(UUID userId, MultipartFile file) {
        return upload(userId, file, "avatar", AVATAR_MAX_BYTES);
    }

    @Transactional
    public User uploadBanner(UUID userId, MultipartFile file) {
        return upload(userId, file, "banner", BANNER_MAX_BYTES);
    }

    private User upload(UUID userId, MultipartFile file, String kind, long maxBytes) {
        User user = users.findById(userId).orElseThrow(
            () -> new com.gakbisa.backend.config.AppExceptions.NotFoundException("Pengguna tidak ditemukan"));
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Pilih berkas foto terlebih dahulu");
        }
        if (file.getSize() > maxBytes) {
            throw new ValidationException("Ukuran foto maksimal " + (maxBytes / 1024 / 1024) + " MB");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Foto tidak dapat dibaca");
        }

        String detected = detectImage(content);
        if (detected == null) {
            throw new ValidationException("Hanya JPG, PNG, atau WEBP yang diperbolehkan");
        }

        try {
            Path directory = root.resolve(kind);
            Files.createDirectories(directory);
            String fileName = userId + "-" + UUID.randomUUID() + "." + detected;
            Path target = directory.resolve(fileName);
            Files.write(target, content);

            String oldPath = "avatar".equals(kind) ? user.getAvatarPath() : user.getBannerPath();
            deleteStoredUrl(oldPath);

            String publicUrl = "/media/profile/" + kind + "/" + fileName;
            if ("avatar".equals(kind)) user.setAvatarPath(publicUrl);
            else user.setBannerPath(publicUrl);
            return users.save(user);
        } catch (IOException e) {
            throw new ValidationException("Foto gagal disimpan");
        }
    }

    private void deleteStoredUrl(String publicUrl) {
        if (publicUrl == null || !publicUrl.startsWith("/media/profile/")) return;
        String relative = publicUrl.substring("/media/profile/".length());
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static String detectImage(byte[] content) {
        if (content.length >= 3
            && (content[0] & 0xff) == 0xff
            && (content[1] & 0xff) == 0xd8
            && (content[2] & 0xff) == 0xff) {
            return "jpg";
        }
        if (content.length >= 8
            && content[0] == (byte) 0x89 && content[1] == 0x50
            && content[2] == 0x4e && content[3] == 0x47
            && content[4] == 0x0d && content[5] == 0x0a
            && content[6] == 0x1a && content[7] == 0x0a) {
            return "png";
        }
        if (content.length >= 12
            && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
            && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "webp";
        }
        return null;
    }
}
