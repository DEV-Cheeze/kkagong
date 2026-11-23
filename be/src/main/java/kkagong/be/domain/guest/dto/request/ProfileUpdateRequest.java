package kkagong.be.domain.guest.dto.request;

import jakarta.validation.constraints.NotNull;
import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import org.hibernate.validator.constraints.Length;

public record ProfileUpdateRequest(
    @Length(min = 1, max = 12, message = "닉네임은 1~12자 이하여야 합니다.")
    String nickname,
    DrinkType drinkType,
    CharacterType characterType
) {

  public ProfileUpdateRequest {
    if (nickname != null) {
      nickname = nickname.strip();
    }
  }

}
