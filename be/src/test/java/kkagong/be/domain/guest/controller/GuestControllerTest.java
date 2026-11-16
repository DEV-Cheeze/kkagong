package kkagong.be.domain.guest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Locale;
import java.util.stream.Stream;
import kkagong.be.domain.guest.dto.request.GuestSignUpRequest;
import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import kkagong.be.domain.guest.service.GuestService;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import kkagong.be.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GuestControllerTest {

  private static final String SIGN_UP_URL = "/api/v1/guests/signup";
  private static final String COOKIE_NAME = "guestId";
  private static final String TOKEN = "a".repeat(64);
  private static final int COOKIE_MAX_AGE = 30 * 24 * 60 * 60;

  @Mock
  private GuestService guestService;

  @Captor
  private ArgumentCaptor<GuestSignUpRequest> requestCaptor;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(new GuestController(guestService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("validRequests")
  @DisplayName("유효한 가입 요청은 서비스에 전달되고 204와 빈 본문, 30일 인증 쿠키를 반환한다")
  void validRequestReturnsNoContentAndCookie(
      String scenario, String json, GuestSignUpRequest expectedRequest) throws Exception {
    when(guestService.guestSignUp(any(GuestSignUpRequest.class))).thenReturn(TOKEN);

    postJson(json)
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(cookie().value(COOKIE_NAME, TOKEN))
        .andExpect(cookie().maxAge(COOKIE_NAME, COOKIE_MAX_AGE))
        .andExpect(cookie().path(COOKIE_NAME, "/"))
        .andExpect(cookie().httpOnly(COOKIE_NAME, true));

    verify(guestService).guestSignUp(requestCaptor.capture());
    assertThat(requestCaptor.getValue()).isEqualTo(expectedRequest);
  }

  private static Stream<Arguments> validRequests() {
    return Stream.of(
        Arguments.of("모든 프로필 지정", """
            {
              "nickname": "공부하는사람",
              "characterType": "Default_Female",
              "drinkType": "CafeLatte_ICE"
            }
            """, new GuestSignUpRequest(
            "공부하는사람", CharacterType.Default_Female, DrinkType.CafeLatte_ICE)),
        Arguments.of("프로필 필드 생략", "{}",
            new GuestSignUpRequest(null, null, null)),
        Arguments.of("프로필 필드에 null 지정", """
            {"nickname":null,"characterType":null,"drinkType":null}
            """, new GuestSignUpRequest(null, null, null)),
        Arguments.of("닉네임 길이 하한 1자", """
            {"nickname":"가"}
            """, new GuestSignUpRequest("가", null, null)),
        Arguments.of("닉네임 길이 상한 12자", """
            {"nickname":"123456789012"}
            """, new GuestSignUpRequest("123456789012", null, null))
    );
  }

  @ParameterizedTest
  @ValueSource(strings = {
      " 123456789012",
      "123456789012 ",
      " 123456789012 "
  })
  @DisplayName("원문이 12자를 넘어도 앞뒤 공백 제거 후 12자인 닉네임은 가입을 허용한다")
  void nicknameAtMaxLengthAfterTrimmingIsAccepted(String nickname) throws Exception {
    when(guestService.guestSignUp(any(GuestSignUpRequest.class))).thenReturn(TOKEN);

    postJson("""
        {"nickname":"%s"}
        """.formatted(nickname))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(cookie().value(COOKIE_NAME, TOKEN));

    verify(guestService).guestSignUp(any(GuestSignUpRequest.class));
  }

  @Test
  @DisplayName("HTTPS 가입 응답의 인증 쿠키에는 Secure 속성이 있어야 한다")
  void httpsSignUpUsesSecureCookie() throws Exception {
    when(guestService.guestSignUp(any(GuestSignUpRequest.class))).thenReturn(TOKEN);

    postJson("{}")
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(cookie().value(COOKIE_NAME, TOKEN))
        .andExpect(cookie().secure(COOKIE_NAME, true));
  }

  @Test
  @DisplayName("인증 쿠키의 SameSite를 명시하고 None이면 Secure도 함께 설정한다")
  void authenticationCookieHasExplicitSameSitePolicy() throws Exception {
    when(guestService.guestSignUp(any(GuestSignUpRequest.class))).thenReturn(TOKEN);

    postJson("{}")
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(result -> {
          Cookie authenticationCookie = result.getResponse().getCookie(COOKIE_NAME);
          assertThat(authenticationCookie).isNotNull();
          String sameSite = authenticationCookie.getAttribute("SameSite");
          assertThat(sameSite).isNotBlank();
          assertThat(sameSite.toLowerCase(Locale.ROOT)).isIn("lax", "strict", "none");
          if (sameSite.equalsIgnoreCase("None")) {
            assertThat(authenticationCookie.getSecure()).isTrue();
          }
        });
  }

  @Test
  @DisplayName("닉네임이 13자면 400을 반환하고 서비스 호출과 인증 쿠키 발급을 하지 않는다")
  void tooLongNicknameIsRejectedBeforeCallingService() throws Exception {
    postJson("""
        {"nickname":"%s"}
        """.formatted("a".repeat(13)))
        .andExpect(status().isBadRequest())
        .andExpect(cookie().doesNotExist(COOKIE_NAME));

    verifyNoInteractions(guestService);
  }

  @ParameterizedTest(name = "지원하지 않는 {0}")
  @CsvSource({
      "characterType, UNKNOWN_CHARACTER",
      "drinkType, UNKNOWN_DRINK"
  })
  @DisplayName("정의되지 않은 프로필 타입은 400으로 거부하고 서비스를 호출하지 않는다")
  void unknownEnumValueIsRejected(String field, String value) throws Exception {
    postJson("""
        {"%s":"%s"}
        """.formatted(field, value))
        .andExpect(status().isBadRequest())
        .andExpect(cookie().doesNotExist(COOKIE_NAME));

    verifyNoInteractions(guestService);
  }

  @Test
  @DisplayName("잘못된 JSON은 400으로 거부하고 서비스를 호출하지 않는다")
  void malformedJsonIsRejected() throws Exception {
    postJson("{")
        .andExpect(status().isBadRequest())
        .andExpect(cookie().doesNotExist(COOKIE_NAME));

    verifyNoInteractions(guestService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "null"})
  @DisplayName("요청 본문이 없거나 null이면 400으로 거부하고 서비스를 호출하지 않는다")
  void missingRequestBodyIsRejected(String body) throws Exception {
    postJson(body)
        .andExpect(status().isBadRequest())
        .andExpect(cookie().doesNotExist(COOKIE_NAME));

    verifyNoInteractions(guestService);
  }

  @Test
  @DisplayName("지원하지 않는 Content-Type은 415로 거부한다")
  void unsupportedContentTypeIsRejected() throws Exception {
    mockMvc.perform(post(SIGN_UP_URL)
            .secure(true)
            .contentType(MediaType.TEXT_PLAIN)
            .content("nickname=test"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(cookie().doesNotExist(COOKIE_NAME));

    verifyNoInteractions(guestService);
  }

  @Test
  @DisplayName("가입 URL에 GET 요청을 보내면 405를 반환하고 POST 허용 정보를 제공한다")
  void unsupportedHttpMethodIsRejected() throws Exception {
    mockMvc.perform(get(SIGN_UP_URL))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string(HttpHeaders.ALLOW, containsString("POST")))
        .andExpect(cookie().doesNotExist(COOKIE_NAME));

    verifyNoInteractions(guestService);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("serviceFailures")
  @DisplayName("서비스 실패 시 오류 HTTP 상태와 ApiResponse를 반환하고 인증 쿠키를 발급하지 않는다")
  void serviceFailureReturnsErrorWithoutCookie(
      String scenario, RuntimeException failure, int expectedStatus, String expectedMessage)
      throws Exception {
    when(guestService.guestSignUp(any(GuestSignUpRequest.class))).thenThrow(failure);

    postJson("{}")
        .andExpect(status().is(expectedStatus))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value(expectedStatus))
        .andExpect(jsonPath("$.message").value(expectedMessage))
        .andExpect(jsonPath("$.data").value(nullValue()))
        .andExpect(cookie().doesNotExist(COOKIE_NAME))
        .andExpect(content().string(not(containsString(TOKEN))))
        .andExpect(content().string(not(containsString("internal-test-detail"))));

    verify(guestService).guestSignUp(new GuestSignUpRequest(null, null, null));
  }

  private static Stream<Arguments> serviceFailures() {
    return Stream.of(
        Arguments.of("도메인 검증 실패",
            new ErrorException(ErrorCode.NICKNAME_TOO_LONG),
            400, "닉네임은 1~12글자"),
        Arguments.of("DB 저장 실패",
            new DataIntegrityViolationException("internal-test-detail"),
            500, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),
        Arguments.of("토큰 발급 실패",
            new IllegalStateException("internal-test-detail"),
            500, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
    );
  }

  private ResultActions postJson(String json) throws Exception {
    return mockMvc.perform(post(SIGN_UP_URL)
        .secure(true)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json));
  }
}
