package kkagong.be.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이나 입력값이 올바르지 않습니다."),
  NICKNAME_TOO_LONG(HttpStatus.BAD_REQUEST, "닉네임은 12글자 이하여야합니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인가되지 않은 접근입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
  GUEST_NOT_FOUND(HttpStatus.BAD_REQUEST, "사용자를 찾을 수 없습니다.");

  private final HttpStatus httpStatus;
  private final String message;
}
