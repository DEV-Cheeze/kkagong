package kkagong.be.domain.guest.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import kkagong.be.domain.guest.dto.request.GuestSignUpRequest;
import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

  @Test
  @DisplayName("닉네임이 null이면 미선택으로 보고 기본 닉네임을 적용한다")
  void nullNicknameUsesDefault() {
    Guest guest = Guest.create(CharacterType.Default_Male, null, DrinkType.Americano_ICE);

    assertThat(guest.getNickname()).isEqualTo("까공이");
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {" ", "   ", "\t\n"})
  @DisplayName("첫 생성의 빈 문자열이나 공백뿐인 닉네임은 기본값을 적용하고 다른 선택값은 유지한다")
  void blankNicknameUsesDefault(String nickname) {
    Guest guest = Guest.create(CharacterType.Default_Female, nickname, DrinkType.Americano_ICE);

    assertThat(guest.getNickname()).isEqualTo("까공이");
    assertThat(guest.getCharacterType()).isEqualTo(CharacterType.Default_Female);
    assertThat(guest.getDrinkType()).isEqualTo(DrinkType.Americano_ICE);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validNicknames")
  @DisplayName("닉네임은 앞뒤 공백을 제거한 뒤 1~12자를 허용하고 내부 공백은 유지한다")
  void nicknameIsValidatedAfterSignupRequestNormalization(
      String scenario, String nickname, String expectedNickname) {
    GuestSignUpRequest request = new GuestSignUpRequest(
        nickname, CharacterType.Default_Male, DrinkType.Americano_ICE);
    Guest guest = Guest.create(request.characterType(), request.nickname(), request.drinkType());

    assertThat(guest.getNickname()).isEqualTo(expectedNickname);
  }

  private static Stream<Arguments> validNicknames() {
    return Stream.of(
        Arguments.of("길이 하한 1자", "가", "가"),
        Arguments.of("길이 상한 12자", "123456789012", "123456789012"),
        Arguments.of("공백 제거 후 1자", " 가 ", "가"),
        Arguments.of("앞뒤 공백 제거", " 까공 ", "까공"),
        Arguments.of("원문은 14자이지만 공백 제거 후 12자",
            " 123456789012 ", "123456789012"),
        Arguments.of("닉네임 내부 공백 유지", " 공부 하는 사람 ", "공부 하는 사람")
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {"1234567890123", " 1234567890123 "})
  @DisplayName("앞뒤 공백을 제거한 뒤에도 13자인 닉네임은 거부한다")
  void nicknameLongerThan12AfterTrimmingIsRejected(String nickname) {
    GuestSignUpRequest request = new GuestSignUpRequest(
        nickname, CharacterType.Default_Male, DrinkType.Americano_ICE);

    assertThatThrownBy(() ->
        Guest.create(request.characterType(), request.nickname(), request.drinkType()))
        .isInstanceOfSatisfying(ErrorException.class,
            exception -> assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_TOO_LONG));
  }
}
