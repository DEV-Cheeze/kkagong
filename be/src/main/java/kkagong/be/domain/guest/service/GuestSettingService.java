package kkagong.be.domain.guest.service;

import jakarta.transaction.Transactional;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.entity.GuestSetting;
import kkagong.be.domain.guest.repository.GuestSettingRepository;
import kkagong.be.domain.timer.entity.TimerSettings;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuestSettingService {

  private final GuestSettingRepository guestSettingRepository;
  private final ApplicationEventPublisher events;

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
    TimerSettings previous = new TimerSettings(setting.getFocusMinutes(),
        setting.getShortBreakMinutes(), setting.getLongBreakMinutes(), setting.isAutoTransition());
    setting.changeTimerSettings(focusMinutes, shortBreakMinutes, longBreakMinutes, autoTransition);
    // 현재 구간의 예고에 영향을 주는 변경은 커밋 성공 후 타이머 서비스에서 알린다.
    if (previous.autoTransition() != setting.isAutoTransition()
        || previous.focusMinutes() != setting.getFocusMinutes()) {
      events.publishEvent(new TimerSettingsChanged(guestId, previous));
    }
    return setting;
  }

}
