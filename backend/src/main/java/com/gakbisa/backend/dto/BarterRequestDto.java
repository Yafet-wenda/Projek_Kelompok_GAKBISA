package com.gakbisa.backend.dto;

import java.util.UUID;

/**
 * DTO untuk form kirim permintaan barter.
 * Digunakan oleh Thymeleaf th:object pada template barter/request.html
 */
public class BarterRequestDto {

    private UUID offeredUserSkillId;
    private UUID wantedUserSkillId;
    private String message;

    public BarterRequestDto() {}

    public BarterRequestDto(UUID offeredUserSkillId, UUID wantedUserSkillId, String message) {
        this.offeredUserSkillId = offeredUserSkillId;
        this.wantedUserSkillId = wantedUserSkillId;
        this.message = message;
    }

    public UUID getOfferedUserSkillId() {
        return offeredUserSkillId;
    }

    public void setOfferedUserSkillId(UUID offeredUserSkillId) {
        this.offeredUserSkillId = offeredUserSkillId;
    }

    public UUID getWantedUserSkillId() {
        return wantedUserSkillId;
    }

    public void setWantedUserSkillId(UUID wantedUserSkillId) {
        this.wantedUserSkillId = wantedUserSkillId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
