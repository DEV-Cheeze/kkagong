package kkagong.be.domain.guest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import kkagong.be.domain.guest.dto.request.GuestSignUpRequest;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import kkagong.be.domain.guest.repository.GuestRepository;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

  private static final String RAW_TOKEN = "1".repeat(64);
  private static final String TOKEN_HASH = "2".repeat(64);

  @Mock
  private SessionTokenGenerator sessionTokenGenerator;

  @Mock
  private GuestRepository guestRepository;

  @InjectMocks
  private GuestService guestService;

  @Captor
  private ArgumentCaptor<Guest> guestCaptor;

  @Test
  @DisplayName("가입 시 클라이언트용 토큰을 반환하고 저장소에는 토큰 해시와 선택한 프로필을 전달한다")
  void returnsRawTokenAndSavesHashWithProfile() {
    when(sessionTokenGenerator.generate()).thenReturn(RAW_TOKEN);
    when(sessionTokenGenerator.hashForLookup(RAW_TOKEN)).thenReturn(TOKEN_HASH);

    String result = guestService.guestSignUp(validRequest());

    assertThat(result).isEqualTo(RAW_TOKEN);
    verify(guestRepository).save(guestCaptor.capture());
    Guest guest = guestCaptor.getValue();
    assertThat(guest.getAuthTokenHash()).isEqualTo(TOKEN_HASH).isNotEqualTo(RAW_TOKEN);
    assertThat(guest.getNickname()).isEqualTo("공부하는사람");
    assertThat(guest.getCharacterType()).isEqualTo(CharacterType.Default_Female);
    assertThat(guest.getDrinkType()).isEqualTo(DrinkType.CafeLatte_ICE);
  }

  @Test
  @DisplayName("닉네임 검증에 실패하면 토큰을 발급하거나 저장소를 호출하지 않는다")
  void invalidNicknamePreventsTokenGenerationAndSave() {
    GuestSignUpRequest request = new GuestSignUpRequest(
        "a".repeat(13), CharacterType.Default_Female, DrinkType.CafeLatte_ICE);

    assertThatThrownBy(() -> guestService.guestSignUp(request))
        .isInstanceOfSatisfying(ErrorException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_TOO_LONG));
    verifyNoInteractions(sessionTokenGenerator, guestRepository);
  }

  @Test
  @DisplayName("토큰 발급 실패를 전파하고 게스트를 저장하지 않는다")
  void tokenGenerationFailurePreventsSave() {
    IllegalStateException failure = new IllegalStateException("토큰 발급 실패");
    when(sessionTokenGenerator.generate()).thenThrow(failure);

    assertThatThrownBy(() -> guestService.guestSignUp(validRequest())).isSameAs(failure);
    verifyNoInteractions(guestRepository);
  }

  @Test
  @DisplayName("토큰 해시 계산 실패를 전파하고 게스트를 저장하지 않는다")
  void tokenHashingFailurePreventsSave() {
    IllegalStateException failure = new IllegalStateException("토큰 해시 계산 실패");
    when(sessionTokenGenerator.generate()).thenReturn(RAW_TOKEN);
    when(sessionTokenGenerator.hashForLookup(RAW_TOKEN)).thenThrow(failure);

    assertThatThrownBy(() -> guestService.guestSignUp(validRequest())).isSameAs(failure);
    verifyNoInteractions(guestRepository);
  }

  @Test
  @DisplayName("저장 실패 시 토큰을 정상 반환하지 않고 저장 예외를 전파한다")
  void saveFailureIsPropagated() {
    DataIntegrityViolationException failure = new DataIntegrityViolationException("게스트 저장 실패");
    when(sessionTokenGenerator.generate()).thenReturn(RAW_TOKEN);
    when(sessionTokenGenerator.hashForLookup(RAW_TOKEN)).thenReturn(TOKEN_HASH);
    when(guestRepository.save(any(Guest.class))).thenThrow(failure);

    assertThatThrownBy(() -> guestService.guestSignUp(validRequest())).isSameAs(failure);
  }

  private GuestSignUpRequest validRequest() {
    return new GuestSignUpRequest(
        "공부하는사람", CharacterType.Default_Female, DrinkType.CafeLatte_ICE);
  }
}
