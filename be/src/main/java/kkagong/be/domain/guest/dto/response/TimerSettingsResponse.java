package kkagong.be.domain.guest.dto.response;

import kkagong.be.domain.guest.entity.GuestSetting;

public record TimerSettingsResponse(
    int focusMinutes,
    int shortBreakMinutes,
    int longBreakMinutes,
    boolean autoTransition
) {

  // 일부 항목만 변경해도 저장된 타이머 설정 전체를 반환한다.
  public static TimerSettingsResponse from(GuestSetting setting) {
    return new TimerSettingsResponse(setting.getFocusMinutes(), setting.getShortBreakMinutes(),
        setting.getLongBreakMinutes(), setting.isAutoTransition());
  }
}
