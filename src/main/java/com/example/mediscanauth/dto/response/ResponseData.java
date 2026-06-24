package com.example.mediscanauth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResponseData<T> {

    private Integer status;
    private String message;
    private String error;
    private String path;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
