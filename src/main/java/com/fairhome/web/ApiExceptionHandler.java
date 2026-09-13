package com.fairhome.web;

import com.fairhome.application.IntakeException;
import com.fairhome.draw.DrawService;
import com.fairhome.rules.RuleValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IntakeException.class)
    public ResponseEntity<Map<String, Object>> intake(IntakeException e) {
        log.debug("FairHome : ApiExceptionHandler : in method intake : START");
        log.warn("FairHome : ApiExceptionHandler : in method intake : rejected intake : {}", e.getMessage());
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.UNPROCESSABLE_ENTITY,
                "The application was not accepted.", Map.of("fieldErrors", e.getFieldErrors()));
        log.debug("FairHome : ApiExceptionHandler : in method intake : END");
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        log.debug("FairHome : ApiExceptionHandler : in method validation : START");
        Map<String, String> fieldErrors = new TreeMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        log.warn("FairHome : ApiExceptionHandler : in method validation : validation failed : {}",
                fieldErrors);
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.BAD_REQUEST,
                "Some fields need attention.", Map.of("fieldErrors", fieldErrors));
        log.debug("FairHome : ApiExceptionHandler : in method validation : END");
        return response;
    }

    @ExceptionHandler(RuleValidationException.class)
    public ResponseEntity<Map<String, Object>> rules(RuleValidationException e) {
        log.debug("FairHome : ApiExceptionHandler : in method rules : START");
        log.warn("FairHome : ApiExceptionHandler : in method rules : validation failed : {}",
                e.getProblems());
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.UNPROCESSABLE_ENTITY,
                "The rule book was not published.", Map.of("problems", e.getProblems()));
        log.debug("FairHome : ApiExceptionHandler : in method rules : END");
        return response;
    }

    @ExceptionHandler(DrawService.DrawRefusedException.class)
    public ResponseEntity<Map<String, Object>> drawRefused(DrawService.DrawRefusedException e) {
        log.debug("FairHome : ApiExceptionHandler : in method drawRefused : START");
        log.warn("FairHome : ApiExceptionHandler : in method drawRefused : draw refused : {}",
                e.getMessage());
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.CONFLICT, e.getMessage(), Map.of());
        log.debug("FairHome : ApiExceptionHandler : in method drawRefused : END");
        return response;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException e) {
        log.debug("FairHome : ApiExceptionHandler : in method badRequest : START");
        log.warn("FairHome : ApiExceptionHandler : in method badRequest : not found or bad request : {}",
                e.getMessage());
        ResponseEntity<Map<String, Object>> response = body(HttpStatus.BAD_REQUEST, e.getMessage(), Map.of());
        log.debug("FairHome : ApiExceptionHandler : in method badRequest : END");
        return response;
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message,
                                                     Map<String, Object> extra) {
        log.debug("FairHome : ApiExceptionHandler : in method body : START");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("message", message);
        body.putAll(extra);
        ResponseEntity<Map<String, Object>> response = ResponseEntity.status(status).body(body);
        log.debug("FairHome : ApiExceptionHandler : in method body : END");
        return response;
    }
}
