package com.gakbisa.backend.controller;

import com.gakbisa.backend.config.AppExceptions.ForbiddenException;
import com.gakbisa.backend.domain.User;
import com.gakbisa.backend.domain.UserRole;
import com.gakbisa.backend.security.GakbisaUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/** Akses identitas pengguna yang sedang login untuk controller. */
public final class CurrentUser {

    private CurrentUser() {}

    private static User requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof GakbisaUserDetails d)) {
            throw new ForbiddenException("Pengguna tidak login");
        }
        return d.getUser();
    }

    public static UUID id() {
        return requireUser().getId();
    }

    public static boolean isAdmin() {
        return requireUser().getRole() == UserRole.ADMIN;
    }
}
