package kkagong.be.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import kkagong.be.domain.guest.service.AuthService;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GuestAuthenticationFilterTest {

  private static final String TOKEN = "a".repeat(64);
  private static final Long GUEST_ID = 42L;

  @Mock
  private AuthService authService;

  @Mock
  private FilterChain nextFilter;

  private GuestAuthenticationFilter filter;
  private MockHttpServletRequest request;
  private MockHttpServletResponse response;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    filter = new GuestAuthenticationFilter(authService);
    request = new MockHttpServletRequest("GET", "/api/v1/guests/me");
    response = new MockHttpServletResponse();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("쿠키가 없으면 미인증 상태로 다음 필터에 전달한다")
  void missingCookiesContinueWithoutAuthentication() throws Exception {
    filter.doFilter(request, response, nextFilter);

    verify(nextFilter).doFilter(request, response);
    verifyNoInteractions(authService);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("게스트 인증과 무관한 쿠키만 있으면 인증 서비스를 호출하지 않는다")
  void unrelatedCookieDoesNotAuthenticate() throws Exception {
    request.setCookies(new Cookie("language", "ko"));

    filter.doFilter(request, response, nextFilter);

    verify(nextFilter).doFilter(request, response);
    verifyNoInteractions(authService);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {" ", "   ", "\t"})
  @DisplayName("인증 쿠키가 빈 값이나 공백이면 미인증 상태로 다음 필터에 전달한다")
  void blankTokenContinuesWithoutAuthentication(String token) throws Exception {
    request.setCookies(new Cookie("guestId", token));

    filter.doFilter(request, response, nextFilter);

    verify(nextFilter).doFilter(request, response);
    verifyNoInteractions(authService);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  @Test
  @DisplayName("검증 성공 후 다음 필터에는 원본 토큰 대신 확정된 게스트 ID가 인증 정보로 전달된다")
  void verifiedGuestIdIsAuthenticatedBeforeContinuing() throws Exception {
    request.setCookies(new Cookie("language", "ko"), new Cookie("guestId", TOKEN));
    when(authService.validateToken(TOKEN)).thenReturn(GUEST_ID);
    doAnswer(invocation -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      assertThat(authentication).isNotNull();
      assertThat(authentication.isAuthenticated()).isTrue();
      assertThat(authentication.getPrincipal()).isEqualTo(GUEST_ID).isNotEqualTo(TOKEN);
      assertThat(authentication.getCredentials()).isNull();
      return null;
    }).when(nextFilter).doFilter(request, response);

    filter.doFilter(request, response, nextFilter);

    verify(authService).validateToken(TOKEN);
    verify(nextFilter).doFilter(request, response);
  }

  @ParameterizedTest
  @MethodSource("authenticationFailures")
  @DisplayName("인증 예외는 앞단 처리기로 전파하며 인증 등록과 후속 요청 실행을 중단한다")
  void authenticationFailureStopsTheRequest(RuntimeException failure) {
    request.setCookies(new Cookie("guestId", TOKEN));
    when(authService.validateToken(TOKEN)).thenThrow(failure);

    assertThatThrownBy(() -> filter.doFilter(request, response, nextFilter)).isSameAs(failure);

    verifyNoInteractions(nextFilter);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }

  private static Stream<RuntimeException> authenticationFailures() {
    return Stream.of(
        new ErrorException(ErrorCode.UNAUTHORIZED),
        new ErrorException(ErrorCode.EXPIRED_TOKEN),
        new DataAccessResourceFailureException("internal-database-detail")
    );
  }

  @ParameterizedTest
  @EnumSource(value = ErrorCode.class, names = {"UNAUTHORIZED", "EXPIRED_TOKEN"})
  @DisplayName("앞단 예외 처리 필터와 연결하면 인증 실패는 401과 공통 ApiResponse로 반환한다")
  void authenticationErrorReturnsUnauthorizedApiResponse(ErrorCode errorCode) throws Exception {
    request.setCookies(new Cookie("guestId", TOKEN));
    when(authService.validateToken(TOKEN)).thenThrow(new ErrorException(errorCode));

    invokeWithExceptionHandler();

    assertErrorResponse(errorCode, 401);
  }

  @Test
  @DisplayName("인증 DB 장애는 401로 바꾸지 않고 500 공통 응답으로 반환하며 내부 정보를 노출하지 않는다")
  void databaseFailureReturnsInternalServerErrorApiResponse() throws Exception {
    request.setCookies(new Cookie("guestId", TOKEN));
    when(authService.validateToken(TOKEN))
        .thenThrow(new DataAccessResourceFailureException("internal-database-detail"));

    invokeWithExceptionHandler();

    assertErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR, 500);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .doesNotContain("internal-database-detail");
  }

  private void invokeWithExceptionHandler() throws Exception {
    ExceptionHandlerFilter exceptionHandlerFilter =
        new ExceptionHandlerFilter(JsonMapper.builder().build());
    exceptionHandlerFilter.doFilter(request, response,
        (servletRequest, servletResponse) ->
            filter.doFilter(servletRequest, servletResponse, nextFilter));
  }

  private void assertErrorResponse(ErrorCode errorCode, int expectedStatus) throws Exception {
    String content = response.getContentAsString(StandardCharsets.UTF_8);
    JsonNode body = JsonMapper.builder().build().readTree(content);

    assertThat(response.getStatus()).isEqualTo(expectedStatus);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(body.get("code").asInt()).isEqualTo(expectedStatus);
    assertThat(body.get("message").asText()).isEqualTo(errorCode.getMessage());
    assertThat(body.get("data").isNull()).isTrue();
    assertThat(content).doesNotContain(TOKEN);
    assertThat(response.getCookies()).isEmpty();
    verifyNoInteractions(nextFilter);
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
