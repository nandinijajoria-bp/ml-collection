package com.bharatpe.lending.lendingplatform.lms.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ObjectConversionException extends RuntimeException {

    private final String message;

    public ObjectConversionException(String message) {
        this.message = message;
    }
}
