package kkagong.be.domain.guest.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import kkagong.be.domain.guest.dto.response.SoundInfoResponse;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.entity.GuestSoundSetting;
import kkagong.be.domain.guest.entity.id.GuestSoundSettingId;
import kkagong.be.domain.guest.enums.SoundCodes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestSoundSettingRepository extends JpaRepository<GuestSoundSetting, GuestSoundSettingId> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
  SELECT g FROM Guest g WHERE g.guestId = :guestId
  """)
  Optional<Guest> getGuestForUpdate(@Param("guestId") Long guestId);

  @Query("""
   SELECT g.id.soundCode, g.volume, g.enabled FROM GuestSoundSetting g WHERE g.id.guestId = :guestId AND g.id.soundCode IN (:soundCodes)
   """)
  List<SoundInfoResponse> findAllByGuestId(@Param("guestId") Long guestId, @Param("soundCodes") List<SoundCodes> soundCodes);
}
