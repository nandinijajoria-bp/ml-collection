package com.bharatpe.lending.advices;


import com.bharatpe.lending.controller.AutoPayUPIController;
import com.bharatpe.lending.dto.Response;
import com.bharatpe.lending.exceptions.InvalidRequestException;
import com.bharatpe.lending.exceptions.ResponseNotSuccessException;
import com.bharatpe.lending.exceptions.UnauthorizedUserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestControllerAdvice(
        assignableTypes = {
                AutoPayUPIController.class
        }
)
public class ControllerAdvisor {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Response> handleAPIError(ResponseStatusException responseStatusException) {
        Response baseApiResponse = new Response("UDS-" + responseStatusException.getStatus().value(), responseStatusException.getReason(),responseStatusException.getMessage());
        return ResponseEntity.status(responseStatusException.getStatus()).body(baseApiResponse);
    }

//    @ExceptionHandler(FeatureNotEnabledException.class)
//    public ResponseEntity<BaseApiResponse> handleFeatureNotFound(FeatureNotEnabledException exception){
//        BaseApiResponse baseApiResponse = new BaseApiResponse("UDS-403", exception.getMessage());
//        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(baseApiResponse);
//    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception e) {
        log.error("Exception: ", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(UnauthorizedUserException.class)
    public void handleUnauthorizedUserException(UnauthorizedUserException e){
        log.error("UserUnauthorized exception: ",e);

    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<HashMap<String, String>> handleInvalidRequestException(InvalidRequestException exception) {
        log.error("[INVALID REQUEST EXCEPTION] occurred", exception);
        return new ResponseEntity<>(
                new HashMap<String, String>() {
                    {
                        put("message", exception.getMessage());
                    }
                },
                BAD_REQUEST);
    }

    @ExceptionHandler(ResponseNotSuccessException.class)
    public ResponseEntity<Map<String, String>> handleResponseNotFoundException(ResponseNotSuccessException exception) {
        log.error("[RESPONSE NOT FOUND EXCEPTION] occurred", exception);
        Map<String, String> response = new HashMap<>();
        response.put("message", exception.getMessage());

        return new ResponseEntity<>(response, NOT_FOUND);
    }
}
