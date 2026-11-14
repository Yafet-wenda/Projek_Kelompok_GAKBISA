package com.gakbisa.service;

import com.gakbisa.dto.Forms.ProfileForm;
import com.gakbisa.dto.Views.ProfileView;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserService {
    ProfileView getOwnProfile(String email);
    ProfileForm getProfileForm(String email);
    /** avatar boleh null/kosong; validasi tipe & ukuran dilakukan di service. */
    void updateProfile(String email, ProfileForm form, MultipartFile avatar);
    /** Portofolio PRIVATE & yang di-hide tidak ikut ditampilkan. viewerEmail boleh null (tamu). */
    ProfileView getPublicProfile(UUID userId, String viewerEmail);
}
