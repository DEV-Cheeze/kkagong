package kkagong.be.domain.guest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GuestServiceTest {

  private static final String RAW_TOKEN = "1".repeat(64);
  private static final String TOKEN_HASH = "2".repeat(64);

  @Mock
  private SessionTokenGenerator sessionTokenGenerator;

  @Mock
  private GuestRepository guestRepository;

  @Mock
  private GuestSettingService guestSettingService;

  @InjectMocks
  private GuestService guestService;

  @Captor
  private ArgumentCaptor<Guest> guestCaptor;

  @Test
  @DisplayName("가입 시 해시 사전 조회 없이 프로필과 해시를 저장하고 원본 토큰을 반환한다")
  void returnsRawTokenAndSavesHashWithProfile() {
    when(sessionTokenGenerator.generate()).thenReturn(RAW_TOKEN);
    when(sessionTokenGenerator.hashForLookup(RAW_TOKEN)).thenReturn(TOKEN_HASH);

    String result = guestService.guestSignUp(validRequest());

    assertThat(result).isEqualTo(RAW_TOKEN);
    verify(guestRepository).save(guestCaptor.capture());
    verifyNoMoreInteractions(guestRepository);
    Guest guest = guestCaptor.getValue();
    verify(guestSettingService).initialize(guest);
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
  @DisplayName("DB에서 중복 해시 저장을 거부하면 토큰 반환 없이 저장 예외를 전파한다")
  void duplicateTokenHashSaveFailureIsPropagated() {
    DataIntegrityViolationException failure = new DataIntegrityViolationException("토큰 해시 중복");
    when(sessionTokenGenerator.generate()).thenReturn(RAW_TOKEN);
    when(sessionTokenGenerator.hashForLookup(RAW_TOKEN)).thenReturn(TOKEN_HASH);
    when(guestRepository.save(any(Guest.class))).thenThrow(failure);

    assertThatThrownBy(() -> guestService.guestSignUp(validRequest())).isSameAs(failure);
  }

  @Test
  @DisplayName("재방문할 게스트가 없으면 GUEST_NOT_FOUND를 반환하고 저장소를 변경하지 않는다")
  void visitForMissingGuestDoesNotRenewExpiration() {
    long guestId = 42L;
    when(guestRepository.findById(guestId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> guestService.getMeAndRenewVisit(guestId))
        .isInstanceOfSatisfying(ErrorException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.GUEST_NOT_FOUND));

    verify(guestRepository).findById(guestId);
    verifyNoMoreInteractions(guestRepository);
    verifyNoInteractions(sessionTokenGenerator, guestSettingService);
  }

  @Test
  @DisplayName("기간 갱신은 해당 게스트 ID와 서버 현재 시각을 저장소에 전달하고 새 토큰을 발급하지 않는다")
  void renewalUsesServerTimeForTheSpecifiedGuest() {
    Long guestId = 42L;
    LocalDateTime now = LocalDateTime.of(2026, 11, 22, 12, 0);

    try (MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
      time.when(LocalDateTime::now).thenReturn(now);

      guestService.renewGuestExpiration(guestId);
    }

    verify(guestRepository).renewGuestExpiration(guestId, now);
    verifyNoInteractions(sessionTokenGenerator);
  }

  @Test
  @DisplayName("기간 갱신 DB 장애는 정상 처리로 삼키지 않고 전파한다")
  void renewalFailureIsPropagated() {
    Long guestId = 42L;
    LocalDateTime now = LocalDateTime.of(2026, 11, 22, 12, 0);
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("기간 갱신 실패");
    doThrow(failure).when(guestRepository).renewGuestExpiration(guestId, now);

    try (MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
      time.when(LocalDateTime::now).thenReturn(now);

      assertThatThrownBy(() -> guestService.renewGuestExpiration(guestId)).isSameAs(failure);
    }

    verifyNoInteractions(sessionTokenGenerator);
  }

  private GuestSignUpRequest validRequest() {
    return new GuestSignUpRequest(
        "공부하는사람", CharacterType.Default_Female, DrinkType.CafeLatte_ICE);
  }
}
