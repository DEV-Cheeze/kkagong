package kkagong.be.global.exception;

import kkagong.be.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(ErrorException.class)
  public ApiResponse<Void> handleErrorException(ErrorException exception) {
    ErrorCode errorCode = exception.getErrorCode();
    log.warn("요청 처리 실패: errorCode={}", errorCode);
    return ApiResponse.error(errorCode);
  }

  @ExceptionHandler(Exception.class)
  public ApiResponse<Void> handleUnexpectedException(Exception exception) {
    log.error("처리하지 못한 요청 예외", exception);
    ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
    return ApiResponse.error(errorCode);
  }
}
