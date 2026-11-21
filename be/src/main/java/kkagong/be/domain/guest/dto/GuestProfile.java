package kkagong.be.domain.guest.dto;

import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import lombok.Builder;

@Builder
public record GuestProfile(
    String nickname,
    CharacterType characterType,
    DrinkType drinkType
) {


  public static GuestProfile from(Guest guest){
    return GuestProfile.builder()
        .nickname(guest.getNickname())
        .characterType(guest.getCharacterType())
        .drinkType(guest.getDrinkType())
        .build();
  }

}
