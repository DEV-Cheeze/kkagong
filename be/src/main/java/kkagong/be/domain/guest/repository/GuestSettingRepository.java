package kkagong.be.domain.guest.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import kkagong.be.domain.guest.entity.GuestSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestSettingRepository extends JpaRepository<GuestSetting, Long> {

  // 부분 변경의 기준이 되는 설정 행을 트랜잭션이 끝날 때까지 잠근다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<GuestSetting> findByGuestId(Long guestId);

  @Query(
  """
  SELECT s.focusMinutes FROM GuestSetting s WHERE s.guestId = :guestId
  """
  )
  Integer getFocusMinutes(@Param("guestId") Long guestId);

}
