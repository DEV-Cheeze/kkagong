package kkagong.be.global.response;

import kkagong.be.global.exception.ErrorCode;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationProperties.Http;
import org.springframework.http.HttpStatus;

public record ApiResponse<T>(

    int code,
    String message,
    T data
) {
  public static <T> ApiResponse<T> ok(T data){
    return new ApiResponse<>(HttpStatus.OK.value(), "응답 성공", data);
  }

  public static <T> ApiResponse<T> create(T data) {
    return new ApiResponse<>(HttpStatus.CREATED.value(), "생성 성공", data);
  }

  public static ApiResponse<Void> error(ErrorCode errorCode) {
    return new ApiResponse<>(errorCode.getHttpStatus().value(), errorCode.getMessage(), null);
  }

  public static ApiResponse<Void> error(HttpStatus httpStatus, String message) {
    return new ApiResponse<>(httpStatus.value(), message, null);
  }

}
