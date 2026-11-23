package kkagong.be.domain.guest.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.entity.GuestSoundSetting;
import kkagong.be.domain.guest.entity.id.GuestSoundSettingId;
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

}
