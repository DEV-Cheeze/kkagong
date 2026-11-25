package kkagong.be.domain.guest.repository;

import kkagong.be.domain.guest.entity.GuestSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestSettingRepository extends JpaRepository<GuestSetting, Long> {

  @Query(
  """
  SELECT s.focusMinutes FROM GuestSetting s WHERE s.guestId = :guestId
  """
  )
  Integer getFocusMinutes(@Param("guestId") Long guestId);

}
