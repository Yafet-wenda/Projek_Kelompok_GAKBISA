package com.gakbisa.controller;

import com.gakbisa.exception.BusinessException;
import com.gakbisa.exception.ResourceNotFoundException;
import com.gakbisa.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.security.Principal;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final NotificationService notificationService;

    public GlobalControllerAdvice(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Badge notifikasi di navbar (${unreadCount}). */
    @ModelAttribute("unreadCount")
    public long unreadCount(Principal principal) {
        return principal == null ? 0L : notificationService.unreadCount(principal.getName());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(ResourceNotFoundException ex, Model model) {
        model.addAttribute("message", ex.getMessage());
        return "error/404";
    }

    @ExceptionHandler({BusinessException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String badRequest(Exception ex, Model model) {
        model.addAttribute("message",
                ex instanceof BusinessException ? ex.getMessage() : "Parameter permintaan tidak valid");
        return "error/400";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String tooLarge(Model model) {
        model.addAttribute("message", "Ukuran file melebihi batas maksimum 10 MB");
        return "error/400";
    }
}
