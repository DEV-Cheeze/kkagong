package kkagong.be.domain.guest.service;

import jakarta.transaction.Transactional;
import kkagong.be.domain.guest.dto.request.GuestSignUpRequest;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.repository.GuestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GuestService {

  private final SessionTokenGenerator sessionTokenGenerator;
  private final GuestRepository guestRepository;

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
    
    return token;
  }


}
