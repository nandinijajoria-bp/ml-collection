package com.bharatpe.lending.lendingplatform.lms.dto.response;

import lombok.Data;

@Data
public class Error {
    private String errorStatusCode;
    private String errorMessage;

    public Error() {
    }

    public Error(String errorStatusCode, String errorMessage) {
        this.errorStatusCode = errorStatusCode;
        this.errorMessage = errorMessage;
    }
}
