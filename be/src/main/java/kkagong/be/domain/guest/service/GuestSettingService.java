package kkagong.be.domain.guest.service;

import jakarta.transaction.Transactional;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.entity.GuestSetting;
import kkagong.be.domain.guest.repository.GuestSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuestSettingService {

  private final GuestSettingRepository guestSettingRepository;

  @Transactional
  public void initialize(Guest guest){ //초기 설정 Insert문

    GuestSetting guestSetting = new GuestSetting(guest);
    guestSettingRepository.save(guestSetting);

  }

}
