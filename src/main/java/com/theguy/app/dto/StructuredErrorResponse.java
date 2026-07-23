package com.theguy.app.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class StructuredErrorResponse {
    private boolean success;
    private String errorCode;
    private String message;
    private List<FieldError> errors;
    private String timestamp;
}
