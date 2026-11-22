package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfileMediaServiceTest {

    @TempDir
    Path tempDir;

    private ProfileMediaService service(UserRepository users) {
        return new ProfileMediaService(users, tempDir.toString());
    }

    @Test
    void uploadAvatarAcceptsPng() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", png);

        User saved = service(users).uploadAvatar(userId, file);

        assertNotNull(saved.getAvatarPath());
        assertTrue(saved.getAvatarPath().startsWith("/media/profile/avatar/"));
        assertTrue(saved.getAvatarPath().endsWith(".png"));
    }

    @Test
    void uploadAvatarRejectsNonImage() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        UserRepository users = mock(UserRepository.class);
        when(users.findById(userId)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        assertThrows(ValidationException.class,
            () -> service(users).uploadAvatar(userId, file));
    }
}
