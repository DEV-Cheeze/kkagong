package kkagong.be.domain.guest.entity.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import kkagong.be.domain.guest.enums.SoundCodes;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class GuestSoundSettingId implements Serializable {

  @Column(name = "guest_id")
  private Long guestId;

  @Column(name = "sound_code")
  @Enumerated(EnumType.STRING)
  private SoundCodes soundCode;
}
