package com.example.mediscanauth.exception.customize;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Slf4j
public class CommonException extends RuntimeException {
    HttpStatus httpStatus;
    Object data;

    public CommonException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        log.error(message, cause);
    }

    public CommonException(String message) {
        super(message);
        this.httpStatus = HttpStatus.BAD_REQUEST;
        log.error(message);
    }
}
