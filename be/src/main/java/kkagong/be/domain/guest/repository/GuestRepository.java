package kkagong.be.domain.guest.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import kkagong.be.domain.guest.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestRepository extends JpaRepository<Guest, Long> {


  Optional<Guest> findByAuthTokenHash(String authTokenHash);

  @Modifying
  @Query("""
  UPDATE Guest SET expiresAt = :now + 30 day, lastSeenAt = :now WHERE guestId = :id
  """)
  void renewGuestExpiration(@Param("id") Long guestId, @Param("now") LocalDateTime now);
}
