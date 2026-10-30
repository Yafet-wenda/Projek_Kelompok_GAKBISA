package com.gakbisa.backend.service;

import com.gakbisa.backend.config.AppExceptions.ValidationException;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.dto.RegisterRequest;
import com.gakbisa.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Skenario uji §8.2 no.1: email duplikat ditolak dengan pesan jelas. */
class AuthServiceTest {

    private UserRepository users;
    private PasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setup() {
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        service = new AuthService(users, encoder);
    }

    @Test
    void registrasiEmailBaruSuksesDanPasswordDiHash() {
        when(users.existsByEmail(anyString())).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(java.util.UUID.randomUUID());
            return u;
        });

        var dto = service.register(new RegisterRequest("Yafet", "YAFET@Example.COM ", "rahasia123", "PENS"));

        assertEquals("yafet@example.com", dto.email(), "email harus dinormalisasi huruf kecil");
        assertEquals(UserRole.STUDENT.name(), dto.role());
        verify(users).save(argThat(u -> encoder.matches("rahasia123", u.getPasswordHash())));
    }

    @Test
    void registrasiEmailDuplikatDitolak() {
        when(users.existsByEmail("ada@gakbisa.test")).thenReturn(true);

        var ex = assertThrows(ValidationException.class,
            () -> service.register(new RegisterRequest("Ada", "ada@gakbisa.test", "rahasia123", null)));
        assertTrue(ex.getMessage().contains("sudah terdaftar"));
        verify(users, never()).save(any());
    }
}
