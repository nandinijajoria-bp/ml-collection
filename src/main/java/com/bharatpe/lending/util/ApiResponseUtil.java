package com.bharatpe.lending.util;

import com.bharatpe.lending.dto.ApiResponseDTOV2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class ApiResponseUtil {

  /**
   * Creates a successful response with 200 OK status
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> ok(T data, String message) {
    return buildResponse(HttpStatus.OK, data, message, null);
  }

  /**
   * Creates a successful response with 201 Created status
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> created(T data, String message) {
    return buildResponse(HttpStatus.CREATED, data, message, null);
  }

  /**
   * Creates a response with 204 No Content status
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> noContent(String message) {
    return buildResponse(HttpStatus.NO_CONTENT, null, message, null);
  }

  /**
   * Creates a response for 400 Bad Request
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> badRequest(String message, String errors) {
    return buildResponse(HttpStatus.BAD_REQUEST, null, message, errors);
  }

  /**
   * Creates a response for 401 Unauthorized
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> unauthorized(String message, String errors) {
    return buildResponse(HttpStatus.UNAUTHORIZED, null, message, errors);
  }

  /**
   * Creates a response for 403 Forbidden
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> forbidden(String message, String errors) {
    return buildResponse(HttpStatus.FORBIDDEN, null, message, errors);
  }

  /**
   * Creates a response for 404 Not Found
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> notFound(String message, String errors) {
    return buildResponse(HttpStatus.NOT_FOUND, null, message, errors);
  }

  /**
   * Creates a response for 409 Conflict
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> conflict(String message, String errors) {
    return buildResponse(HttpStatus.CONFLICT, null, message, errors);
  }

  /**
   * Creates a response for 422 Unprocessable Entity
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> unprocessableEntity(String message, String errors) {
    return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, null, message, errors);
  }

  /**
   * Creates a response for 500 Internal Server Error
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> internalError(String message, String errors) {
    return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, null, message, errors);
  }

  /**
   * Creates a response for 503 Service Unavailable
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> serviceUnavailable(String message, String errors) {
    return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, null, message, errors);
  }

  /**
   * Creates a response for 504 Gateway Timeout
   */
  public static <T> ResponseEntity<ApiResponseDTOV2<T>> gatewayTimeout(String message, String errors) {
    return buildResponse(HttpStatus.GATEWAY_TIMEOUT, null, message, errors);
  }

  /**
   * Creates a standardized response with the given status, data, message and errors
   */
  private static <T> ResponseEntity<ApiResponseDTOV2<T>> buildResponse(HttpStatus status, T data, String message, String errors) {
    ApiResponseDTOV2<T> responseBody = ApiResponseDTOV2.<T>builder()
            .status(status.value())
            .message(message != null ? message : status.getReasonPhrase())
            .data(data)
            .errors(errors)
            .build();

    return new ResponseEntity<>(responseBody, status);
  }
}