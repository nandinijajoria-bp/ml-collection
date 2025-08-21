package com.bharatpe.lending.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class ApiResponseUtil {

  /**
   * Creates a successful response with 200 OK status
   */
  public static <T> ResponseEntity<Map<String, Object>> ok(T data, String message) {
    return buildResponse(HttpStatus.OK, data, message, null);
  }

  /**
   * Creates a successful response with 201 Created status
   */
  public static <T> ResponseEntity<Map<String, Object>> created(T data, String message) {
    return buildResponse(HttpStatus.CREATED, data, message, null);
  }

  /**
   * Creates a response with 204 No Content status
   */
  public static ResponseEntity<Map<String, Object>> noContent(String message) {
    return buildResponse(HttpStatus.NO_CONTENT, null, message, null);
  }

  /**
   * Creates a response for 400 Bad Request
   */
  public static ResponseEntity<Map<String, Object>> badRequest(String message, Object errors) {
    return buildResponse(HttpStatus.BAD_REQUEST, null, message, errors);
  }

  /**
   * Creates a response for 401 Unauthorized
   */
  public static ResponseEntity<Map<String, Object>> unauthorized(String message, Object errors) {
    return buildResponse(HttpStatus.UNAUTHORIZED, null, message, errors);
  }

  /**
   * Creates a response for 403 Forbidden
   */
  public static ResponseEntity<Map<String, Object>> forbidden(String message, Object errors) {
    return buildResponse(HttpStatus.FORBIDDEN, null, message, errors);
  }

  /**
   * Creates a response for 404 Not Found
   */
  public static ResponseEntity<Map<String, Object>> notFound(String message, Object errors) {
    return buildResponse(HttpStatus.NOT_FOUND, null, message, errors);
  }

  /**
   * Creates a response for 409 Conflict
   */
  public static ResponseEntity<Map<String, Object>> conflict(String message, Object errors) {
    return buildResponse(HttpStatus.CONFLICT, null, message, errors);
  }

  /**
   * Creates a response for 422 Unprocessable Entity
   */
  public static ResponseEntity<Map<String, Object>> unprocessableEntity(String message, Object errors) {
    return buildResponse(HttpStatus.UNPROCESSABLE_ENTITY, null, message, errors);
  }

  /**
   * Creates a response for 500 Internal Server Error
   */
  public static ResponseEntity<Map<String, Object>> internalError(String message, Object errors) {
    return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, null, message, errors);
  }

  /**
   * Creates a response for 503 Service Unavailable
   */
  public static ResponseEntity<Map<String, Object>> serviceUnavailable(String message, Object errors) {
    return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, null, message, errors);
  }

  /**
   * Creates a response for 504 Gateway Timeout
   */
  public static ResponseEntity<Map<String, Object>> gatewayTimeout(String message, Object errors) {
    return buildResponse(HttpStatus.GATEWAY_TIMEOUT, null, message, errors);
  }

  /**
   * Creates a standardized response with the given status, data, message and errors
   */
  private static <T> ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, T data, String message, Object errors) {
    Map<String, Object> responseBody = Map.of(
            "status", status.value(),
            "message", message != null ? message : status.getReasonPhrase(),
            "data", data,
            "errors", errors
    );

    return new ResponseEntity<>(responseBody, status);
  }
}