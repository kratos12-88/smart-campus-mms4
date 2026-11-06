package com.smartcampus.config;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ForbiddenException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public Map<String,String> forbidden(ForbiddenException e){return Map.of("message",e.getMessage());}
  @ExceptionHandler(AuthException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public Map<String,String> unauthorized(AuthException e){
    return Map.of("message",e.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String,String> conflict(ConflictException e){
    return Map.of("message",e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String,String> badRequest(IllegalArgumentException e){
    return Map.of("message",e.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Map<String,String> serverError(IllegalStateException e){
    return Map.of("message","The server could not complete the request");
  }
}
