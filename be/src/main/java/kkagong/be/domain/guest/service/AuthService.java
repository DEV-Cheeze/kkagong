package kkagong.be.domain.guest.service;

import jakarta.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.repository.GuestRepository;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final GuestRepository guestRepository;
  private final GuestService guestService;
  private final SessionTokenGenerator sessionTokenGenerator;

  @Transactional
  public Long validateToken(String token) {

    String tokenHash = sessionTokenGenerator.hashForLookup(token);

    Guest guest = guestRepository.findByAuthTokenHash(tokenHash).orElseThrow(() -> new ErrorException(
        ErrorCode.UNAUTHORIZED)); //필터 관련 에러이기에 사용자가 존재하지 않는다는 에러를 반환할 경우, 공격자가 추론하기 쉬워 변경

    LocalDateTime now = LocalDateTime.now();

    if(!guest.getExpiresAt().isAfter(now)){ //토큰 만료 검사
      throw new ErrorException(ErrorCode.EXPIRED_TOKEN);
    }

    guestService.renewGuestExpiration(guest.getGuestId());
    return guest.getGuestId();
  }
}
