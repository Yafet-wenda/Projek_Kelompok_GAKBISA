package com.gakbisa.backend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Baris kategori beserta daftar keahlian di bawahnya, untuk halaman kelola admin.
 * Getter biasa (bukan record) karena Thymeleaf membaca properti lewat getter.
 */
public class CategoryRow {

    private final UUID id;
    private final String name;
    private final String description;
    private final List<String> skills;

    public CategoryRow(UUID id, String name, String description, List<String> skills) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.skills = skills;
    }

    public UUID getId() { return id; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public List<String> getSkills() { return skills; }
}
