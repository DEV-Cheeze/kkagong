package kkagong.be.domain.guest.dto.request;

import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import org.hibernate.validator.constraints.Length;

public record GuestSignUpRequest(

    @Length(max = 12, message = "닉네임은 12자 이하여야 합니다.")
    String nickname,

    CharacterType characterType,

    DrinkType drinkType
) {

  public GuestSignUpRequest {
    if (nickname != null) {
      nickname = nickname.trim();
    }
  }
}
