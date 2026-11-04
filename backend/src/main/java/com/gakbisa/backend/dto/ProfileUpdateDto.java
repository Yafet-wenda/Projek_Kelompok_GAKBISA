package com.gakbisa.backend.dto;

/**
 * DTO untuk form edit profil pengguna.
 * Digunakan oleh Thymeleaf th:object pada template profile/edit.html
 */
public class ProfileUpdateDto {

    private String fullName;
    private String bio;
    private String campus;

    public ProfileUpdateDto() {}

    public ProfileUpdateDto(String fullName, String bio, String campus) {
        this.fullName = fullName;
        this.bio = bio;
        this.campus = campus;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getCampus() { return campus; }
    public void setCampus(String campus) { this.campus = campus; }
}

