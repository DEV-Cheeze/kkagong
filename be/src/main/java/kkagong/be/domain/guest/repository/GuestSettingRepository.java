package kkagong.be.domain.guest.repository;

import kkagong.be.domain.guest.entity.GuestSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestSettingRepository extends JpaRepository<GuestSetting, Long> {

}
