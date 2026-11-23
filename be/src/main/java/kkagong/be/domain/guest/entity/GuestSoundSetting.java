package kkagong.be.domain.guest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import kkagong.be.domain.guest.entity.id.GuestSoundSettingId;
import kkagong.be.domain.guest.enums.SoundCodes;
import kkagong.be.global.entity.BaseEntity;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guest_sound_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestSoundSetting extends BaseEntity {

  @EmbeddedId
  private GuestSoundSettingId id;

  @MapsId("guestId")
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "guest_id")
  private Guest guest;

  @Column(name = "volume", nullable = false)
  private int volume = 50;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = false;

  public GuestSoundSetting(
      Guest guest, SoundCodes soundCode) {
    this.guest = guest;
    this.id = new GuestSoundSettingId(guest.getGuestId(), soundCode);
  }

  public void changeSetting(
      Integer volume,
      Boolean enabled
  ) {
    if (volume != null && (volume < 0 || volume > 100)) {
      throw new ErrorException(ErrorCode.INVALID_REQUEST);
    }

    this.volume = volume == null ? this.volume : volume;
    this.enabled = enabled == null ? this.enabled : enabled;
  }

}
