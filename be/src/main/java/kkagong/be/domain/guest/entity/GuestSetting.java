package kkagong.be.domain.guest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import kkagong.be.domain.guest.enums.WorkPose;
import kkagong.be.global.entity.BaseEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guest_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestSetting extends BaseEntity {

  @Id
  @Column(name = "guest_id")
  private Long guestId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "guest_id")
  private Guest guest;

  @Column(name = "focus_minutes", nullable = false)
  private int focusMinutes = 25;

  @Column(name = "short_break_minutes", nullable = false)
  private int shortBreakMinutes = 5;

  @Column(name = "long_break_minutes", nullable = false)
  private int longBreakMinutes = 15;

  @Column(name = "auto_transition", nullable = false)
  private boolean autoTransition = false;

  @Column(name = "share_current_task", nullable = false)
  private boolean shareCurrentTask = true;

  @Column(name = "preferred_work_pose", nullable = false)
  @Enumerated(EnumType.STRING)
  private WorkPose preferredWorkPose = WorkPose.TYPING;

  @Column(name = "end_sound_enabled", nullable = false)
  private boolean endSoundEnabled = true;

  @Column(name = "master_muted", nullable = false)
  private boolean masterMuted = false;

  public GuestSetting(Guest guest) {
    this.guest = guest;
  }

}
