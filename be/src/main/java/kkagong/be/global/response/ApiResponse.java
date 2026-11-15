package kkagong.be.global.response;

import kkagong.be.global.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public record ApiResponse<T>(

    int code,
    String message,
    T data
) {
  public static <T> ApiResponse<T> ok(T data){
    return new ApiResponse<>(HttpStatus.OK.value(), "응답 성공", data);
  }

  public static ApiResponse<Void> error(ErrorCode errorCode) {
    return new ApiResponse<>(errorCode.getHttpStatus().value(), errorCode.getMessage(), null);
  }
}
