package kkagong.be.domain.guest.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuestTest {


  @Test
  @DisplayName("기본 프로필 캐릭터 선택, 닉네임, 커피 중 선택되지 않은 항목이 있을경우 기본값을 따른다.")
  void t1(){

    CharacterType defaultCharacterType = CharacterType.Default_Male; //Default_Male, Default_FeMale
    String defaultNickname = "까공이"; //까공이
    DrinkType defaultDrinkType = DrinkType.Americano_ICE; //Americano_ICE

    //given

    //입력1: 캐릭터 미선택
    CharacterType t1CharacterType = null;
    String t1Nickname = "test";
    DrinkType t1DrinkType = DrinkType.CafeLatte_ICE;

    //입력2: 음료 미선택
    CharacterType t2CharacterType = CharacterType.Default_Female;
    String t2Nickname = "test";
    DrinkType t2DrinkType = null;

    //입력3: 닉네임 미선택
    CharacterType t3CharacterType = CharacterType.Default_Female;
    String t3Nickname = null;
    DrinkType t3DrinkType = DrinkType.CafeLatte_ICE;

    //when
    Guest t0Guest = Guest.create(null, null, null);
    Guest t1Guest = Guest.create(t1CharacterType, t1Nickname, t1DrinkType);
    Guest t2Guest = Guest.create(t2CharacterType, t2Nickname, t2DrinkType);
    Guest t3Guest = Guest.create(t3CharacterType, t3Nickname, t3DrinkType);

    //then
    assertThat(t0Guest.getCharacterType()).isEqualTo(defaultCharacterType);
    assertThat(t0Guest.getNickname()).isEqualTo(defaultNickname);
    assertThat(t0Guest.getDrinkType()).isEqualTo(defaultDrinkType);

    assertThat(t1Guest.getCharacterType()).isEqualTo(defaultCharacterType);
    assertThat(t1Guest.getNickname()).isEqualTo(t1Nickname);
    assertThat(t1Guest.getDrinkType()).isEqualTo(DrinkType.CafeLatte_ICE);

    assertThat(t2Guest.getCharacterType()).isEqualTo(CharacterType.Default_Female);
    assertThat(t2Guest.getNickname()).isEqualTo(t2Nickname);
    assertThat(t2Guest.getDrinkType()).isEqualTo(defaultDrinkType);

    assertThat(t3Guest.getCharacterType()).isEqualTo(CharacterType.Default_Female);
    assertThat(t3Guest.getNickname()).isEqualTo(defaultNickname);
    assertThat(t3Guest.getDrinkType()).isEqualTo(DrinkType.CafeLatte_ICE);

  }


}