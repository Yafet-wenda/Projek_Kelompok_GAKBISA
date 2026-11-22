package com.gakbisa.backend.config;

import com.gakbisa.backend.controller.WebController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = WebController.class)
@Order(-1)
public class WebExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ModelAndView handle(Exception error, HttpServletRequest request) {
        HttpStatus status = error instanceof AppExceptions.NotFoundException ? HttpStatus.NOT_FOUND
            : error instanceof AppExceptions.ForbiddenException ? HttpStatus.FORBIDDEN
            : error instanceof AppExceptions.UnauthorizedException ? HttpStatus.UNAUTHORIZED
            : error instanceof AppExceptions.ValidationException || error instanceof ConstraintViolationException
                || error instanceof IllegalArgumentException
                || error instanceof org.springframework.validation.BindException
                || error instanceof org.springframework.web.bind.MissingServletRequestParameterException
                || error instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
                ? HttpStatus.BAD_REQUEST
            : HttpStatus.INTERNAL_SERVER_ERROR;
        if (status.is5xxServerError()) log.error("Halaman web gagal: {} ({})", request.getRequestURI(), error.getClass().getSimpleName());
        var view = new ModelAndView("error");
        view.setStatus(status);
        view.addObject("status", status.value());
        view.addObject("path", request.getRequestURI());
        return view;
    }
}
