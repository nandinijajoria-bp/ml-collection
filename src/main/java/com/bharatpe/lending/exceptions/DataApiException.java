package com.bharatpe.lending.exceptions;

public class DataApiException extends RuntimeException {
  public static class InvalidInputException extends RuntimeException {
    public InvalidInputException(String message) {
      super(message);
    }
  }

  public static class MerchantNotFoundException extends RuntimeException {
    public MerchantNotFoundException(String message) {
      super(message);
    }
  }

  public static class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class OperationNotAllowedException extends RuntimeException {
    public OperationNotAllowedException(String message) {
      super(message);
    }
  }
}
