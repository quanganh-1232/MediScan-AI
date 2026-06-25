package com.example.mediscanauth.exception;

import com.example.mediscanauth.dto.response.ResponseData;
import com.example.mediscanauth.exception.customize.CommonException;
import com.example.mediscanauth.exception.customize.InvalidFieldException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class RestControllerGlobalExceptionHandler {

    @ExceptionHandler({CommonException.class})
    public ResponseEntity<ResponseData<?>> handleCommonException(CommonException e, WebRequest request) {
        return ResponseEntity.status(e.getHttpStatus())
                .body(ResponseData.builder()
                        .status(e.getHttpStatus().value())
                        .message(e.getMessage())
                        .data(e.getData())
                        .path(getPath(request))
                        .error(e.getHttpStatus().getReasonPhrase())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseData<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (a, b) -> a));
        ResponseData<Map<String, String>> body = ResponseData.<Map<String, String>>builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation failed")
                .data(fieldErrors)
                .error("Bad Request")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ResponseData<?>> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<String> violations = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("violations", violations);
        ResponseData<Map<String, Object>> body = ResponseData.<Map<String, Object>>builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Constraint violations")
                .data(data)
                .error("Bad Request")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseData<?>> handleNotReadable(HttpMessageNotReadableException ex, WebRequest request) {
        ResponseData<Void> body = ResponseData.<Void>builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Malformed JSON request")
                .error("Bad Request")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseData<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        ResponseData<Void> body = ResponseData.<Void>builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED.value())
                .message("Method not allowed")
                .error("Method Not Allowed")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseData<?>> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        ResponseData<Void> body = ResponseData.<Void>builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Access is denied")
                .error("Forbidden")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseData<?>> handleAuth(AuthenticationException ex, WebRequest request) {
        ResponseData<Void> body = ResponseData.<Void>builder()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message("Unauthorized")
                .error("Unauthorized")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ResponseData<?>> handleNotFound(RuntimeException ex, WebRequest request) {
        ResponseData<Void> body = ResponseData.<Void>builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message("Resource not found")
                .error("Not Found")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseData<?>> handleConflict(DataIntegrityViolationException ex, WebRequest request) {
        ResponseData<Void> body = ResponseData.<Void>builder()
                .status(HttpStatus.CONFLICT.value())
                .message("Data integrity violation")
                .error("Conflict")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidFieldException.class)
    public ResponseEntity<ResponseData<?>> handleInvalidField(InvalidFieldException ex, WebRequest request) {
        ResponseData<Void> body = ResponseData.<Void>builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .error("Bad Request")
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseData<?>> handleGeneric(Exception ex, WebRequest request) {
        ResponseData<Void> body = ResponseData.<Void>builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Internal server error")
                .error(ex.getMessage())
                .path(getPath(request))
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String getPath(WebRequest request) {
        try {
            if (request instanceof ServletWebRequest servletWebRequest) {
                return servletWebRequest.getRequest().getRequestURI();
            }
        } catch (Exception ignored) {}
        return request.getDescription(false);
    }
}
