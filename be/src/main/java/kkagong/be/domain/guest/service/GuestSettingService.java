package kkagong.be.domain.guest.service;

import jakarta.transaction.Transactional;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.entity.GuestSetting;
import kkagong.be.domain.guest.repository.GuestSettingRepository;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuestSettingService {

  private final GuestSettingRepository guestSettingRepository;

  @Transactional
  public void initialize(Guest guest){ //초기 설정 Insert문

    GuestSetting guestSetting = new GuestSetting(guest);
    guestSettingRepository.save(guestSetting);

  }

  @Transactional
  public GuestSetting updateTimerSettings(Long guestId, Integer focusMinutes,
      Integer shortBreakMinutes, Integer longBreakMinutes, Boolean autoTransition) {
    // 여러 탭에서 수정해도 같은 설정 행을 순서대로 읽고 변경한다.
    GuestSetting setting = guestSettingRepository.findByGuestId(guestId)
        .orElseThrow(() -> new ErrorException(ErrorCode.GUEST_NOT_FOUND));

    // 검증에 성공한 변경은 트랜잭션이 끝날 때 JPA 더티 체킹으로 저장된다.
    setting.changeTimerSettings(focusMinutes, shortBreakMinutes, longBreakMinutes, autoTransition);
    return setting;
  }

}
