package kkagong.be.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kkagong.be.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler{

  //Handling Business Errors
  @ExceptionHandler(ErrorException.class)
  public ApiResponse<Void> handleErrorException(ErrorException exception, HttpServletResponse response) {
    ErrorCode errorCode = exception.getErrorCode();
    log.warn("요청 처리 실패: errorCode={}", errorCode);

    response.setStatus(errorCode.getHttpStatus().value());

    return ApiResponse.error(errorCode);
  }

  //Handling Validation Errors
  @ExceptionHandler({
      MethodArgumentNotValidException.class,
      HandlerMethodValidationException.class
  })
  public ApiResponse<?> handleMethodArgumentNotValid(Exception e, HttpServletResponse response) {
    String message = "";

    if(e instanceof MethodArgumentNotValidException methodException){
      FieldError err = methodException.getBindingResult().getFieldError();
      message = err == null ? ErrorCode.INVALID_REQUEST.getMessage() : methodException.getBindingResult().getFieldError().getDefaultMessage();
    }

    if(e instanceof HandlerMethodValidationException handlerException){
      message = handlerException.getParameterValidationResults()
          .getFirst()
          .getResolvableErrors()
          .getFirst()
          .getDefaultMessage();
    }

    response.setStatus(HttpStatus.BAD_REQUEST.value());

    log.warn("MethodArgumentNotValidException {}", e.getMessage());
    return ApiResponse.error(HttpStatus.BAD_REQUEST,message);
  }

  //Handling Other Errors
  @ExceptionHandler(Exception.class)
  public ApiResponse<Void> handleUnexpectedException(Exception exception, HttpServletResponse response) {
    log.error("처리하지 못한 요청 예외", exception);
    ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    HttpStatus httpStatus = errorCode.getHttpStatus();

    if(exception instanceof HttpRequestMethodNotSupportedException e){
      httpStatus = HttpStatus.METHOD_NOT_ALLOWED;
    }

    if(exception instanceof HttpMessageNotReadableException e){
      httpStatus = HttpStatus.BAD_REQUEST;
    }

    if(exception instanceof HttpMediaTypeNotSupportedException e){
      httpStatus = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
    }

    response.setStatus(httpStatus.value());

    return ApiResponse.error(errorCode);
  }
}
