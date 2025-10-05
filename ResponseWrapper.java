package com.nishithsingh.diagnostics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseWrapper<T> {
    private HttpStatus status;
    private T body;
    private String errorMessage;

    // Helpers
    public boolean isSuccess() {
        return status != null && status.is2xxSuccessful();
    }
}
