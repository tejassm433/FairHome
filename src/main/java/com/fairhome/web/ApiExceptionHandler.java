package com.fairhome.web;

import com.fairhome.application.IntakeException;
import com.fairhome.draw.DrawService;
import com.fairhome.rules.RuleValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns the domain's refusals into useful JSON. A rejected application or a refused draw is a normal,
 * expected answer with a reason attached, not a stack trace.
 */
@RestControllerAdvice(basePackageClasses = ApiController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(IntakeException.class)
    public ResponseEntity<Map<String, Object>> intake(IntakeException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "The application was not accepted.",
                Map.of("fieldErrors", e.getFieldErrors()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new TreeMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "Some fields need attention.",
                Map.of("fieldErrors", fieldErrors));
    }

    @ExceptionHandler(RuleValidationException.class)
    public ResponseEntity<Map<String, Object>> rules(RuleValidationException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, "The rule book was not published.",
                Map.of("problems", e.getProblems()));
    }

    @ExceptionHandler(DrawService.DrawRefusedException.class)
    public ResponseEntity<Map<String, Object>> drawRefused(DrawService.DrawRefusedException e) {
        return body(HttpStatus.CONFLICT, e.getMessage(), Map.of());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage(), Map.of());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message,
                                                     Map<String, Object> extra) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("message", message);
        body.putAll(extra);
        return ResponseEntity.status(status).body(body);
    }
}
