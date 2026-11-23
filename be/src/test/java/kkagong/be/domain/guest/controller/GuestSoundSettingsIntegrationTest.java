package kkagong.be.domain.guest.controller;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:guest-sound-settings;MODE=PostgreSQL;LOCK_TIMEOUT=10000;"
        + "INIT=CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.hikari.auto-commit=true",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.jpa.show-sql=false",
    "spring.flyway.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuestSoundSettingsIntegrationTest {

  private static final String SETTINGS_URL = "/api/v1/guests/me/sound-settings/{soundCode}";
  private static final String COOKIE_NAME = "guestId";
  private static final String KEYBOARD = "KEYBOARD";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void clearGuests() {
    jdbcTemplate.update("delete from guest_sound_setting");
    jdbcTemplate.update("delete from guest_setting");
    jdbcTemplate.update("delete from guest");
  }

  @Test
  void firstToggleInsertsDefaultsAndFurtherChangesUpdateTheSameRow() throws Exception {
    GuestSession session = signUp("키보드게스트");
    assertThat(storedSettings()).isEmpty();

    patchKeyboard(session, """
        {"enabled": true}
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.soundName").value(KEYBOARD))
        .andExpect(jsonPath("$.data.volume").value(50))
        .andExpect(jsonPath("$.data.enabled").value(true));
    assertThat(storedSettings())
        .containsExactly(new StoredSetting(session.guestId(), KEYBOARD, 50, true));

    patchKeyboard(session, """
        {"volume": 70}
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.volume").value(70))
        .andExpect(jsonPath("$.data.enabled").value(true));

    patchKeyboard(session, """
        {"enabled": false}
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.volume").value(70))
        .andExpect(jsonPath("$.data.enabled").value(false));
    assertThat(storedSettings())
        .containsExactly(new StoredSetting(session.guestId(), KEYBOARD, 70, false));

    patchKeyboard(session, """
        {"enabled": true}
        """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.volume").value(70))
        .andExpect(jsonPath("$.data.enabled").value(true));
    assertThat(storedSettings())
        .containsExactly(new StoredSetting(session.guestId(), KEYBOARD, 70, true));
  }

  @Test
  void firstVolumeChangeUsesCookieOwnerAndDefaultsToDisabled() throws Exception {
    GuestSession session = signUp("내설정게스트");
    GuestSession other = signUp("다른게스트");

    mockMvc.perform(soundPatch(KEYBOARD, """
            {"volume": 70}
            """)
            .cookie(session.cookie())
            .param("guestId", Long.toString(other.guestId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.volume").value(70))
        .andExpect(jsonPath("$.data.enabled").value(false));

    assertThat(storedSettings())
        .containsExactly(new StoredSetting(session.guestId(), KEYBOARD, 70, false));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 100})
  void acceptsBoundaryVolumesAndUpdatesBothFields(int volume) throws Exception {
    GuestSession session = signUp("경계값게스트");

    patchKeyboard(session, """
        {"volume": %d, "enabled": true}
        """.formatted(volume))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.volume").value(volume))
        .andExpect(jsonPath("$.data.enabled").value(true));

    assertThat(storedSettings())
        .containsExactly(new StoredSetting(session.guestId(), KEYBOARD, volume, true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "101", "70.5"})
  void invalidVolumeDoesNotChangeStoredValues(String invalidVolume) throws Exception {
    GuestSession session = signUp("음량검증게스트");
    patchKeyboard(session, """
        {"volume": 30, "enabled": true}
        """).andExpect(status().isOk());
    StoredSetting original = new StoredSetting(session.guestId(), KEYBOARD, 30, true);

    patchKeyboard(session, """
        {"volume": %s, "enabled": false}
        """.formatted(invalidVolume))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));

    assertThat(storedSettings()).containsExactly(original);
  }

  @Test
  void unsupportedSoundIsRejectedWithoutWritingRows() throws Exception {
    GuestSession session = signUp("코드검증게스트");

    mockMvc.perform(soundPatch("UNSUPPORTED", """
            {"enabled": true}
            """).cookie(session.cookie()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));

    assertThat(storedSettings()).isEmpty();
  }

  @Test
  void emptyPatchIsRejectedWithoutCreatingASetting() throws Exception {
    GuestSession session = signUp("빈요청게스트");

    patchKeyboard(session, "{}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(400));

    assertThat(storedSettings()).isEmpty();
  }

  @Test
  void requiresGuestCookie() throws Exception {
    mockMvc.perform(soundPatch(KEYBOARD, """
            {"enabled": true}
            """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));

    assertThat(storedSettings()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void concurrentChangesWaitForGuestLockAndPreserveBothFields(boolean existingSetting)
      throws Exception {
    GuestSession session = signUp("동시수정게스트");
    if (existingSetting) {
      patchKeyboard(session, """
          {"volume": 50, "enabled": false}
          """).andExpect(status().isOk());
    }

    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      Future<MvcResult> secondRequest = transactionTemplate.execute(transaction -> {
        enableKeyboardInsideTransaction(session);
        Integer lockingSession = jdbcTemplate.queryForObject("select session_id()", Integer.class);

        Future<MvcResult> pending = executor.submit(() -> patchKeyboard(session, """
            {"volume": 70}
            """).andReturn());

        // Keep the first PATCH uncommitted until H2 reports that the second PATCH is waiting.
        await("두 번째 PATCH의 행 잠금 대기").pollInSameThread().atMost(5, SECONDS)
            .until(() -> jdbcTemplate.queryForObject("""
                select count(*) from information_schema.sessions
                where blocker_id = ? and session_state = 'BLOCKED'
                """, Long.class, lockingSession) > 0);
        return pending;
      });

      MvcResult result = secondRequest.get(5, SECONDS);
      status().isOk().match(result);
      jsonPath("$.data.volume").value(70).match(result);
      jsonPath("$.data.enabled").value(true).match(result);
    }

    assertThat(storedSettings())
        .containsExactly(new StoredSetting(session.guestId(), KEYBOARD, 70, true));
  }

  private void enableKeyboardInsideTransaction(GuestSession session) {
    try {
      patchKeyboard(session, """
          {"enabled": true}
          """).andExpect(status().isOk());
    } catch (Exception exception) {
      throw new AssertionError("첫 번째 PATCH 실행 실패", exception);
    }
  }

  private GuestSession signUp(String nickname) throws Exception {
    Cookie sessionCookie = mockMvc.perform(post("/api/v1/guests/signup").secure(true)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"nickname": "%s"}
                """.formatted(nickname)))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists(COOKIE_NAME))
        .andReturn().getResponse().getCookie(COOKIE_NAME);
    Long guestId = jdbcTemplate.queryForObject(
        "select guest_id from guest where nickname = ?", Long.class, nickname);
    return new GuestSession(guestId, sessionCookie);
  }

  private ResultActions patchKeyboard(GuestSession session, String body) throws Exception {
    return mockMvc.perform(soundPatch(KEYBOARD, body).cookie(session.cookie()));
  }

  private MockHttpServletRequestBuilder soundPatch(String soundCode, String body) {
    return patch(SETTINGS_URL, soundCode).secure(true)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private List<StoredSetting> storedSettings() {
    return jdbcTemplate.query("select guest_id, sound_code, volume, enabled from guest_sound_setting",
        (row, index) -> new StoredSetting(row.getLong("guest_id"), row.getString("sound_code"),
            row.getInt("volume"), row.getBoolean("enabled")));
  }

  private record GuestSession(long guestId, Cookie cookie) {
  }

  private record StoredSetting(long guestId, String soundCode, int volume, boolean enabled) {
  }
}
