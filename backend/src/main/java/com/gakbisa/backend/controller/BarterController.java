package com.gakbisa.backend.controller;

import com.gakbisa.backend.dto.BarterCreateRequest;
import com.gakbisa.backend.dto.BarterDto;
import com.gakbisa.backend.dto.BarterAgreementStatusDto;
import com.gakbisa.backend.service.BarterAgreementService;
import com.gakbisa.backend.service.BarterService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/barter")
public class BarterController {

    private final BarterService barter;
    private final BarterAgreementService agreements;

    public BarterController(BarterService barter, BarterAgreementService agreements) {
        this.barter = barter;
        this.agreements = agreements;
    }

    public record AgreementRequest(int durationMinutes, int sessionCount) {}

    @GetMapping("/{id}/agreement")
    public BarterAgreementStatusDto agreement(@PathVariable UUID id) {
        return agreements.view(CurrentUser.id(), id);
    }

    @PostMapping("/{id}/agreement")
    public BarterAgreementStatusDto proposeAgreement(@PathVariable UUID id, @RequestBody AgreementRequest request) {
        return agreements.propose(CurrentUser.id(), id, request.durationMinutes(), request.sessionCount());
    }

    @PostMapping("/{id}/agreement/accept")
    public BarterAgreementStatusDto acceptAgreement(@PathVariable UUID id) {
        return agreements.accept(CurrentUser.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BarterDto send(@Valid @RequestBody BarterCreateRequest req) {
        return BarterDto.from(barter.send(CurrentUser.id(), req));
    }

    @GetMapping("/inbox")
    public List<BarterDto> inbox() {
        return barter.inbox(CurrentUser.id()).stream().map(BarterDto::from).toList();
    }

    @GetMapping("/outbox")
    public List<BarterDto> outbox() {
        return barter.outbox(CurrentUser.id()).stream().map(BarterDto::from).toList();
    }

    /** Body: {"accept": true|false} — hanya penerima yang boleh merespons. */
    @PostMapping("/{id}/respond")
    public BarterDto respond(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        boolean accept = Boolean.TRUE.equals(body.get("accept"));
        return BarterDto.from(barter.respond(CurrentUser.id(), id, accept));
    }

    @PostMapping("/{id}/cancel")
    public BarterDto cancel(@PathVariable UUID id) {
        return BarterDto.from(barter.cancel(CurrentUser.id(), id));
    }

    /** Detail satu permintaan barter (hanya pengirim atau penerima). */
    @GetMapping("/{id}")
    public BarterDto view(@PathVariable UUID id) {
        return BarterDto.from(barter.viewFor(CurrentUser.id(), id));
    }
}
