package kkagong.be.domain.guest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.entity.GuestSetting;
import kkagong.be.domain.guest.enums.WorkPose;
import kkagong.be.domain.guest.repository.GuestSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GuestSettingServiceTest {

  @Mock
  private GuestSettingRepository guestSettingRepository;

  @InjectMocks
  private GuestSettingService guestSettingService;

  @Captor
  private ArgumentCaptor<GuestSetting> settingCaptor;

  @Test
  @DisplayName("전달받은 게스트에 명세의 공통 설정 기본값을 연결해 저장한다")
  void initializesDefaultSettingsForTheGivenGuest() {
    Guest guest = Guest.create(null, "공부하는사람", null);

    guestSettingService.initialize(guest);

    verify(guestSettingRepository).save(settingCaptor.capture());
    GuestSetting setting = settingCaptor.getValue();
    assertThat(setting.getGuest()).isSameAs(guest);
    assertThat(setting.getFocusMinutes()).isEqualTo(25);
    assertThat(setting.getShortBreakMinutes()).isEqualTo(5);
    assertThat(setting.getLongBreakMinutes()).isEqualTo(15);
    assertThat(setting.isAutoTransition()).isFalse();
    assertThat(setting.isShareCurrentTask()).isTrue();
    assertThat(setting.getPreferredWorkPose()).isEqualTo(WorkPose.TYPING);
    assertThat(setting.isEndSoundEnabled()).isTrue();
    assertThat(setting.isMasterMuted()).isFalse();
  }

  @Test
  @DisplayName("설정 저장 실패를 삼키지 않고 호출자에게 전파한다")
  void saveFailureIsPropagated() {
    Guest guest = Guest.create(null, "공부하는사람", null);
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException("설정 저장 실패");
    when(guestSettingRepository.save(any(GuestSetting.class))).thenThrow(failure);

    assertThatThrownBy(() -> guestSettingService.initialize(guest)).isSameAs(failure);
  }
}
