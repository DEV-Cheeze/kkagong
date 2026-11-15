package kkagong.be.global.exception;

import java.util.Objects;
import lombok.Getter;

@Getter
public class ErrorException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final ErrorCode errorCode;

  public ErrorException(ErrorCode errorCode) {
    this(errorCode, null);
  }

  public ErrorException(ErrorCode errorCode, Throwable cause) {
    super(Objects.requireNonNull(errorCode, "errorCode는 필수입니다.").getMessage(), cause);
    this.errorCode = errorCode;
  }
}
