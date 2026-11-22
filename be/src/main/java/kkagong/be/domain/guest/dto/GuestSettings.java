package kkagong.be.domain.guest.dto;

import kkagong.be.domain.guest.entity.GuestSetting;
import kkagong.be.domain.guest.enums.WorkPose;
import lombok.Builder;

@Builder
public record GuestSettings(
    int focusMinutes,
    int shortBreakMinutes,
    int longBreakMinutes,
    boolean autoTransition,
    boolean shareCurrentTask,
    WorkPose preferredWorkPose,
    boolean endSoundEnabled,
    boolean masterMuted
) {

  public static GuestSettings from(GuestSetting guestSetting) {
    return GuestSettings.builder()
        .focusMinutes(guestSetting.getFocusMinutes())
        .shortBreakMinutes(guestSetting.getShortBreakMinutes())
        .longBreakMinutes(guestSetting.getLongBreakMinutes())
        .autoTransition(guestSetting.isAutoTransition())
        .shareCurrentTask(guestSetting.isShareCurrentTask())
        .preferredWorkPose(guestSetting.getPreferredWorkPose())
        .endSoundEnabled(guestSetting.isEndSoundEnabled())
        .masterMuted(guestSetting.isMasterMuted())
        .build();
  }

}
