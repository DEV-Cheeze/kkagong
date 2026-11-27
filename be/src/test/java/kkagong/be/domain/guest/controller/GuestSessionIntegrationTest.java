package kkagong.be.domain.guest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.domain.timer.enums.TimerAction;
import kkagong.be.domain.timer.service.InMemoryTimerStore;
import kkagong.be.domain.timer.service.TimerStatePublisher;
import kkagong.be.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = Replace.ANY)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestSessionIntegrationTest {

  private static final String SIGNUP_URL = "/api/v1/guests/signup";
  private static final String ME_URL = "/api/v1/guests/me";
  private static final String TIMER_URL = "/api/v1/timer";
  private static final String TIMER_ACTIONS_URL = TIMER_URL + "/actions";
  private static final String PROFILE_URL = ME_URL + "/profile";
  private static final String TIMER_SETTINGS_URL = ME_URL + "/timer-settings";
  private static final String COOKIE_NAME = "guestId";
  private static final int THIRTY_DAYS_IN_SECONDS = 30 * 24 * 60 * 60;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private InMemoryTimerStore timerStore;

  @MockitoBean
  private TimerStatePublisher publisher;

  @BeforeEach
  void clearGuests() {
    jdbcTemplate.update("delete from guest_setting");
    jdbcTemplate.update("delete from guest");
  }

  @Test
  void timerActionsUseAuthenticatedGuestAndReturnTheActionResult() throws Exception {
    Cookie session = signUp("타이머게스트");
    Long guestId = guestIdOf("타이머게스트");
    jdbcTemplate.update("update guest_setting set focus_minutes = 40 where guest_id = ?", guestId);

    mockMvc.perform(timerActionRequest("""
            {"action":"START"}
            """).cookie(session))
        .andExpect(status().isOk())
        .andExpect(content().json("""
            {"code":200,"message":"응답 성공","data":null}
            """));

    TimerState started = timerStore.find(guestId).orElseThrow();
    assertThat(started.status()).isEqualTo(TimerState.Status.RUNNING);
    assertThat(started.durationMs()).isEqualTo(40 * 60_000L);

    for (String action : new String[] {"PAUSE", "RESUME", "STOP"}) {
      mockMvc.perform(timerActionRequest("""
              {"action":"%s","intervalId":"%s"}
              """.formatted(action, started.intervalId())).cookie(session))
          .andExpect(status().isOk())
          .andExpect(content().json("""
              {"code":200,"message":"응답 성공","data":null}
              """));
    }

    TimerState stopped = timerStore.find(guestId).orElseThrow();
    assertThat(stopped.status()).isEqualTo(TimerState.Status.READY);
    assertThat(stopped.intervalId()).isNull();

    mockMvc.perform(timerActionRequest("""
            {"action":"STOP","intervalId":"%s"}
            """.formatted(started.intervalId())).cookie(session))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(ErrorCode.TIMER_INTERVAL_MISMATCH.getMessage()));
  }

  @Test
  void timerActionsRequireGuestCookie() throws Exception {
    expectUnauthorized(timerActionRequest("""
        {"action":"START"}
        """), ErrorCode.UNAUTHORIZED);
  }

  @Test
  void timerActionRejectsMalformedIntervalId() throws Exception {
    Cookie session = signUp("구간검증게스트");

    mockMvc.perform(timerActionRequest("""
            {"action":"PAUSE","intervalId":"invalid"}
            """).cookie(session))
        .andExpect(status().isBadRequest());

    assertThat(timerStore.find(guestIdOf("구간검증게스트"))).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(TimerAction.class)
  void expiredTimerActionReturnsConflictAndPublishesReadyOnce(TimerAction action) throws Exception {
    Cookie session = signUp("만료조작게스트");
    Long guestId = guestIdOf("만료조작게스트");
    TimerState expired = storeExpiredFocus(guestId);

    mockMvc.perform(timerActionRequest("""
            {"action":"%s","intervalId":"%s"}
            """.formatted(action, expired.intervalId())).cookie(session))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(ErrorCode.TIMER_INTERVAL_MISMATCH.getMessage()));

    TimerState ready = timerStore.find(guestId).orElseThrow();
    assertThat(ready.status()).isEqualTo(TimerState.Status.READY);
    assertThat(ready.phase()).isEqualTo(TimerState.Phase.SHORT_BREAK);
    verify(publisher).publishReady(guestId, ready);
    verifyNoMoreInteractions(publisher);
  }

  @Test
  void timerQueryReturnsReadyWithConfiguredFocusDuration() throws Exception {
    Cookie session = signUp("타이머초기조회");
    Long guestId = guestIdOf("타이머초기조회");
    jdbcTemplate.update("update guest_setting set focus_minutes = 40 where guest_id = ?", guestId);
    assertThat(timerStore.find(guestId)).isEmpty();

    mockMvc.perform(get(TIMER_URL).secure(true).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.phase").value("FOCUS"))
        .andExpect(jsonPath("$.data.status").value("READY"))
        .andExpect(jsonPath("$.data.durationMs").value(40 * 60_000L))
        .andExpect(jsonPath("$.data.serverNow").exists())
        .andExpect(jsonPath("$.data.intervalId").doesNotExist())
        .andExpect(jsonPath("$.data.endsAt").doesNotExist())
        .andExpect(jsonPath("$.data.remainingMs").doesNotExist());

    assertThat(timerStore.find(guestId)).isEmpty();
  }

  @Test
  void timerQueryRestoresPausedRemainingTime() throws Exception {
    Cookie session = signUp("일시정지게스트");
    Long guestId = guestIdOf("일시정지게스트");
    UUID intervalId = UUID.randomUUID();
    long durationMs = 25 * 60_000L;
    long remainingMs = 17 * 60_000L + 24_000L;
    TimerState paused = new TimerState(intervalId, TimerState.Phase.FOCUS, TimerState.Status.PAUSED,
        durationMs, null, null, remainingMs);
    timerStore.update(guestId, current -> paused);

    mockMvc.perform(get(TIMER_URL).secure(true).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.intervalId").value(intervalId.toString()))
        .andExpect(jsonPath("$.data.phase").value("FOCUS"))
        .andExpect(jsonPath("$.data.status").value("PAUSED"))
        .andExpect(jsonPath("$.data.durationMs").value(durationMs))
        .andExpect(jsonPath("$.data.remainingMs").value(remainingMs))
        .andExpect(jsonPath("$.data.endsAt").doesNotExist());

    assertThat(timerStore.find(guestId)).contains(paused);
  }

  @Test
  void timerQueryFinishesExpiredRunningInterval() throws Exception {
    Cookie session = signUp("만료조회게스트");
    Long guestId = guestIdOf("만료조회게스트");
    storeExpiredFocus(guestId);

    mockMvc.perform(get(TIMER_URL).secure(true).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.phase").value("SHORT_BREAK"))
        .andExpect(jsonPath("$.data.status").value("READY"))
        .andExpect(jsonPath("$.data.durationMs").value(5 * 60_000L))
        .andExpect(jsonPath("$.data.intervalId").doesNotExist())
        .andExpect(jsonPath("$.data.endsAt").doesNotExist())
        .andExpect(jsonPath("$.data.remainingMs").doesNotExist());

    TimerState ready = new TimerState(null, TimerState.Phase.SHORT_BREAK,
        TimerState.Status.READY, 5 * 60_000L, null, null, null);
    assertThat(timerStore.find(guestId)).contains(ready);
    verify(publisher).publishReady(guestId, ready);

    mockMvc.perform(get(TIMER_URL).secure(true).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.phase").value("SHORT_BREAK"))
        .andExpect(jsonPath("$.data.status").value("READY"));

    verifyNoMoreInteractions(publisher);
  }

  @Test
  void profileChangesRemainVisibleWithTheSameCookie() throws Exception {
    Cookie session = signUp("기존게스트");
    Long guestId = guestIdOf("기존게스트");

    mockMvc.perform(profilePatch("""
            {"drinkType":"CafeLatte_ICE"}
            """).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("기존게스트"))
        .andExpect(jsonPath("$.data.drinkType").value("CafeLatte_ICE"));

    mockMvc.perform(profilePatch("""
            {"nickname":"\u3000123456789012 "}
            """).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("123456789012"))
        .andExpect(jsonPath("$.data.characterType").value("Default_Male"))
        .andExpect(jsonPath("$.data.drinkType").value("CafeLatte_ICE"));

    mockMvc.perform(post(ME_URL).secure(true).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.guestProfile.nickname").value("123456789012"))
        .andExpect(jsonPath("$.data.guestProfile.characterType").value("Default_Male"))
        .andExpect(jsonPath("$.data.guestProfile.drinkType").value("CafeLatte_ICE"));

    assertThat(guestIdOf("123456789012")).isEqualTo(guestId);
    assertThat(jdbcTemplate.queryForObject("select count(*) from guest", Long.class)).isEqualTo(1L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\u3000", "1234567890123"})
  void invalidNicknameDoesNotChangeTheStoredProfile(String nickname) throws Exception {
    Cookie session = signUp("기존게스트");

    mockMvc.perform(profilePatch("""
            {"nickname":"%s","drinkType":"CafeLatte_ICE"}
            """.formatted(nickname)).cookie(session))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));

    mockMvc.perform(post(ME_URL).secure(true).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.guestProfile.nickname").value("기존게스트"))
        .andExpect(jsonPath("$.data.guestProfile.drinkType").value("Americano_ICE"));
  }

  @Test
  void profilePatchRequiresGuestCookie() throws Exception {
    expectUnauthorized(profilePatch("""
        {"nickname":"까공"}
        """), ErrorCode.UNAUTHORIZED);
  }

  @Test
  void timerSettingsPatchPersistsChangesForTheAuthenticatedGuest() throws Exception {
    Cookie session = signUp("타이머설정게스트");
    Cookie otherSession = signUp("별도설정게스트");

    mockMvc.perform(timerSettingsPatch("""
            {"focusMinutes":50,"shortBreakMinutes":10,"longBreakMinutes":20,"autoTransition":true}
            """).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.focusMinutes").value(50))
        .andExpect(jsonPath("$.data.shortBreakMinutes").value(10))
        .andExpect(jsonPath("$.data.longBreakMinutes").value(20))
        .andExpect(jsonPath("$.data.autoTransition").value(true));

    mockMvc.perform(timerSettingsPatch("""
            {"focusMinutes":20,"autoTransition":false}
            """).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.focusMinutes").value(20))
        .andExpect(jsonPath("$.data.shortBreakMinutes").value(10))
        .andExpect(jsonPath("$.data.longBreakMinutes").value(20))
        .andExpect(jsonPath("$.data.autoTransition").value(false));

    expectTimerSettings(session, 20, 10, 20, false);
    expectTimerSettings(otherSession, 25, 5, 15, false);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}",
      "{\"focusMinutes\":0,\"autoTransition\":true}",
      "{\"focusMinutes\":181,\"autoTransition\":true}",
      "{\"focusMinutes\":25.5,\"autoTransition\":true}",
      "{\"shortBreakMinutes\":5.5,\"autoTransition\":true}",
      "{\"longBreakMinutes\":15.5,\"autoTransition\":true}",
      "{\"focusMinutes\":\"25\",\"autoTransition\":true}"
  })
  void invalidTimerSettingsPatchKeepsStoredSettings(String body) throws Exception {
    Cookie session = signUp("잘못된타이머설정");

    mockMvc.perform(timerSettingsPatch(body).cookie(session))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));

    expectTimerSettings(session, 25, 5, 15, false);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{\"shortBreakMinutes\":16,\"autoTransition\":true}",
      "{\"focusMinutes\":14,\"autoTransition\":true}"
  })
  void timerSettingsPatchValidatesTheMergedDurations(String body) throws Exception {
    Cookie session = signUp("타이머조합검증");

    mockMvc.perform(timerSettingsPatch(body).cookie(session))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_TIMER_SETTINGS.getMessage()));

    expectTimerSettings(session, 25, 5, 15, false);
  }

  @Test
  void timerSettingsPatchRequiresGuestCookie() throws Exception {
    expectUnauthorized(timerSettingsPatch("{\"autoTransition\":true}"), ErrorCode.UNAUTHORIZED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "\u3000"})
  void signupStillDefaultsBlankNicknames(String nickname) throws Exception {
    signUp(nickname);
    assertThat(guestIdOf("까공이")).isNotNull();
  }

  @Test
  void signupCookieRestoresTheSameGuestAndRenewsTheVisit() throws Exception {
    Cookie session = signUp("재방문게스트");
    Long guestId = guestIdOf("재방문게스트");
    signUp("다른게스트");
    Long otherGuestId = guestIdOf("다른게스트");
    LocalDateTime previousVisit = LocalDateTime.now().minusDays(10).withNano(0);
    jdbcTemplate.update("update guest set last_seen_at = ?, expires_at = ? where guest_id = ?",
        previousVisit, previousVisit.plusDays(30), guestId);
    jdbcTemplate.update("update guest_setting set focus_minutes = 45 where guest_id = ?", guestId);
    Visit otherGuestVisit = visitOf(otherGuestId);

    mockMvc.perform(post(ME_URL).secure(true).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.guestProfile.nickname").value("재방문게스트"))
        .andExpect(jsonPath("$.data.guestSettings.focusMinutes").value(45))
        .andExpect(cookie().value(COOKIE_NAME, session.getValue()))
        .andExpect(cookie().maxAge(COOKIE_NAME, THIRTY_DAYS_IN_SECONDS));

    mockMvc.perform(post(ME_URL).secure(true).cookie(session)
            .param("guestId", otherGuestId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.guestProfile.nickname").value("재방문게스트"))
        .andExpect(jsonPath("$.data.guestSettings.focusMinutes").value(45))
        .andExpect(cookie().value(COOKIE_NAME, session.getValue()));

    Visit renewed = visitOf(guestId);
    assertThat(renewed.lastSeenAt()).isAfter(previousVisit);
    assertThat(renewed.expiresAt()).isEqualTo(renewed.lastSeenAt().plusDays(30));
    assertThat(guestIdOf("재방문게스트")).isEqualTo(guestId);
    assertThat(visitOf(otherGuestId)).isEqualTo(otherGuestVisit);
    assertThat(jdbcTemplate.queryForObject("select count(*) from guest", Long.class)).isEqualTo(2L);
  }

  @Test
  void missingCookieReturnsUnauthorized() throws Exception {
    expectUnauthorized(post(ME_URL).secure(true), ErrorCode.UNAUTHORIZED);
  }

  @Test
  void alteredCookieIsRejectedWithoutRenewingTheVisit() throws Exception {
    Cookie session = signUp("변조검증게스트");
    Long guestId = guestIdOf("변조검증게스트");
    Visit previousVisit = visitOf(guestId);
    String token = session.getValue();
    char replacement = token.charAt(0) == '0' ? '1' : '0';
    Cookie alteredSession = new Cookie(COOKIE_NAME, replacement + token.substring(1));

    expectUnauthorized(post(ME_URL).secure(true).cookie(alteredSession), ErrorCode.UNAUTHORIZED);

    assertThat(visitOf(guestId)).isEqualTo(previousVisit);
  }

  @Test
  void expiredCookieIsRejectedWithoutRenewingTheVisit() throws Exception {
    Cookie session = signUp("만료검증게스트");
    Long guestId = guestIdOf("만료검증게스트");
    LocalDateTime expiredAt = LocalDateTime.now().minusDays(1).withNano(0);
    jdbcTemplate.update("update guest set last_seen_at = ?, expires_at = ? where guest_id = ?",
        expiredAt.minusDays(30), expiredAt, guestId);
    Visit previousVisit = visitOf(guestId);

    expectUnauthorized(post(ME_URL).secure(true).cookie(session), ErrorCode.EXPIRED_TOKEN);

    assertThat(visitOf(guestId)).isEqualTo(previousVisit);
  }

  @Test
  void signupRemainsPublicWithAnInvalidCookie() throws Exception {
    mockMvc.perform(signupRequest("처음게스트").cookie(new Cookie(COOKIE_NAME, "invalid")))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists(COOKIE_NAME));
  }

  private TimerState storeExpiredFocus(Long guestId) {
    Instant endsAt = Instant.now().minusSeconds(60);
    long durationMs = 25 * 60_000L;
    // 종료 예약 작업이 실행되기 전의 만료 상태를 재현한다.
    return timerStore.update(guestId, current -> new TimerState(UUID.randomUUID(),
        TimerState.Phase.FOCUS, TimerState.Status.RUNNING, durationMs,
        endsAt.minusMillis(durationMs), endsAt, null));
  }

  private MockHttpServletRequestBuilder timerActionRequest(String body) {
    return post(TIMER_ACTIONS_URL).secure(true).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private MockHttpServletRequestBuilder profilePatch(String body) {
    return patch(PROFILE_URL).secure(true).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private MockHttpServletRequestBuilder timerSettingsPatch(String body) {
    return patch(TIMER_SETTINGS_URL).secure(true).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private void expectTimerSettings(Cookie session, int focus, int shortBreak, int longBreak,
      boolean autoTransition) throws Exception {
    mockMvc.perform(post(ME_URL).secure(true).cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.guestSettings.focusMinutes").value(focus))
        .andExpect(jsonPath("$.data.guestSettings.shortBreakMinutes").value(shortBreak))
        .andExpect(jsonPath("$.data.guestSettings.longBreakMinutes").value(longBreak))
        .andExpect(jsonPath("$.data.guestSettings.autoTransition").value(autoTransition));
  }

  private Cookie signUp(String nickname) throws Exception {
    return mockMvc.perform(signupRequest(nickname))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists(COOKIE_NAME))
        .andReturn().getResponse().getCookie(COOKIE_NAME);
  }

  private MockHttpServletRequestBuilder signupRequest(String nickname) {
    return post(SIGNUP_URL).secure(true)
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {"nickname":"%s"}
            """.formatted(nickname));
  }

  private void expectUnauthorized(MockHttpServletRequestBuilder request, ErrorCode errorCode)
      throws Exception {
    mockMvc.perform(request)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401))
        .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
        .andExpect(jsonPath("$.data").doesNotExist())
        .andExpect(cookie().doesNotExist(COOKIE_NAME));
  }

  private Long guestIdOf(String nickname) {
    return jdbcTemplate.queryForObject("select guest_id from guest where nickname = ?",
        Long.class, nickname);
  }

  private Visit visitOf(Long guestId) {
    return jdbcTemplate.queryForObject("select last_seen_at, expires_at from guest where guest_id = ?",
        (rs, rowNum) -> new Visit(rs.getObject("last_seen_at", LocalDateTime.class),
            rs.getObject("expires_at", LocalDateTime.class)), guestId);
  }

  private record Visit(LocalDateTime lastSeenAt, LocalDateTime expiresAt) {
  }
}
