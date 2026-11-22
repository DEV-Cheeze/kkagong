package kkagong.be.domain.guest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.repository.GuestRepository;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String TOKEN = "a".repeat(64);
  private static final String TOKEN_HASH = "b".repeat(64);
  private static final Long GUEST_ID = 42L;
  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 22, 12, 0);

  @Mock
  private GuestRepository guestRepository;

  @Mock
  private GuestService guestService;

  @Mock
  private SessionTokenGenerator sessionTokenGenerator;

  @Mock
  private Guest guest;

  @InjectMocks
  private AuthService authService;

  @ParameterizedTest
  @ValueSource(longs = {1, 86_400_000_000_000L})
  @DisplayName("만료 전 토큰을 해시로 조회하고 기간 갱신 없이 기존 게스트 ID를 반환한다")
  void validTokenReturnsExistingGuestIdWithoutRenewal(long remainingNanos) {
    stubStoredGuest(NOW.plusNanos(remainingNanos));
    when(guest.getGuestId()).thenReturn(GUEST_ID);

    try (MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
      time.when(LocalDateTime::now).thenReturn(NOW);

      assertThat(authService.validateToken(TOKEN)).isEqualTo(GUEST_ID);
    }

    verify(guestRepository).findByAuthTokenHash(TOKEN_HASH);
    verifyNoInteractions(guestService);
  }

  @Test
  @DisplayName("미등록 토큰은 UNAUTHORIZED로 거부하고 보관 기간을 갱신하지 않는다")
  void unregisteredTokenIsRejectedWithoutRenewal() {
    when(sessionTokenGenerator.hashForLookup(TOKEN)).thenReturn(TOKEN_HASH);
    when(guestRepository.findByAuthTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.validateToken(TOKEN))
        .isInstanceOfSatisfying(ErrorException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

    verify(guestRepository).findByAuthTokenHash(TOKEN_HASH);
    verifyNoInteractions(guestService);
  }

  @ParameterizedTest
  @ValueSource(longs = {-1, 0})
  @DisplayName("만료 시각과 현재가 같거나 이미 지났으면 EXPIRED_TOKEN으로 거부하고 갱신하지 않는다")
  void expiredTokenIncludingExactBoundaryIsRejected(long remainingNanos) {
    stubStoredGuest(NOW.plusNanos(remainingNanos));

    try (MockedStatic<LocalDateTime> time = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
      time.when(LocalDateTime::now).thenReturn(NOW);

      assertThatThrownBy(() -> authService.validateToken(TOKEN))
          .isInstanceOfSatisfying(ErrorException.class,
              exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EXPIRED_TOKEN));
    }

    verifyNoInteractions(guestService);
  }

  @Test
  @DisplayName("해시 계산 실패는 전파하고 DB 조회와 기간 갱신을 실행하지 않는다")
  void hashingFailurePreventsLookupAndRenewal() {
    IllegalStateException failure = new IllegalStateException("해시 계산 실패");
    when(sessionTokenGenerator.hashForLookup(TOKEN)).thenThrow(failure);

    assertThatThrownBy(() -> authService.validateToken(TOKEN)).isSameAs(failure);

    verifyNoInteractions(guestRepository, guestService);
  }

  @Test
  @DisplayName("인증 DB 조회 장애를 인증 실패로 바꾸지 않고 전파하며 기간을 갱신하지 않는다")
  void databaseFailureIsPropagatedWithoutRenewal() {
    DataAccessResourceFailureException failure =
        new DataAccessResourceFailureException("인증 DB 조회 실패");
    when(sessionTokenGenerator.hashForLookup(TOKEN)).thenReturn(TOKEN_HASH);
    when(guestRepository.findByAuthTokenHash(TOKEN_HASH)).thenThrow(failure);

    assertThatThrownBy(() -> authService.validateToken(TOKEN)).isSameAs(failure);

    verifyNoInteractions(guestService);
  }

  private void stubStoredGuest(LocalDateTime expiresAt) {
    when(sessionTokenGenerator.hashForLookup(TOKEN)).thenReturn(TOKEN_HASH);
    when(guestRepository.findByAuthTokenHash(TOKEN_HASH)).thenReturn(Optional.of(guest));
    when(guest.getExpiresAt()).thenReturn(expiresAt);
  }
}
