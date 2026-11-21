package kkagong.be.domain.guest.service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import kkagong.be.domain.guest.dto.GuestProfile;
import kkagong.be.domain.guest.dto.GuestSettings;
import kkagong.be.domain.guest.dto.request.GuestSignUpRequest;
import kkagong.be.domain.guest.dto.response.GuestMeResponse;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.entity.GuestSetting;
import kkagong.be.domain.guest.repository.GuestRepository;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuestService {

  private final SessionTokenGenerator sessionTokenGenerator;
  private final GuestRepository guestRepository;
  private final GuestSettingService guestSettingService;

  @Transactional
  public String guestSignUp(GuestSignUpRequest request){
    //닉네임, 커피, 캐릭터를 종합한 기본 객체 생성
    Guest guest = Guest.create(request.characterType(), request.nickname(), request.drinkType());

    //해시 값 계산 후 객체에 지정
    String token = sessionTokenGenerator.generate();
    String hash = sessionTokenGenerator.hashForLookup(token);

    guest.assignToken(hash);

    //Sha-256값 리턴
    guestRepository.save(guest);
    guestSettingService.initialize(guest);

    return token;
  }

  @Transactional
  public GuestMeResponse getMeAndRenewVisit(Long guestId){ //첫 입장, 새로고침 시 로딩에 필요한 데이터를 로드하는 기능

    Guest guest = guestRepository.findById(guestId).orElseThrow(() -> new ErrorException(
        ErrorCode.GUEST_NOT_FOUND)); //실제 출력에 비해 전체 데이터를 끌어오는 경향이 있음. 추후 문제 발생 시 구간 분석 및 개선 필요

    GuestSetting guestSetting = guest.getGuestSetting();

    GuestSettings guestSettings = GuestSettings.from(guestSetting);
    GuestProfile guestProfile = GuestProfile.from(guest);

    LocalDateTime now = LocalDateTime.now();

    guestRepository.renewGuestExpiration(guest.getGuestId(), now); //토큰 제한 갱신

    return new GuestMeResponse(guestProfile, guestSettings);
  }

  @Transactional
  public void renewGuestExpiration(Long guestId){

    LocalDateTime now = LocalDateTime.now();
    guestRepository.renewGuestExpiration(guestId, now);

  }

}
