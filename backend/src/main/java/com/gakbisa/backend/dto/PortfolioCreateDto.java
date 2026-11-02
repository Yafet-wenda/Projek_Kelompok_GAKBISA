package com.gakbisa.backend.dto;

/**
 * DTO untuk form upload portofolio.
 * Digunakan oleh Thymeleaf th:object pada template portfolio/upload.html
 */
public class PortfolioCreateDto {

    private String title;
    private String description;
    private String visibility = "PUBLIC";

    public PortfolioCreateDto() {}

    public PortfolioCreateDto(String title, String description, String visibility) {
        this.title = title;
        this.description = description;
        this.visibility = visibility;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
}
