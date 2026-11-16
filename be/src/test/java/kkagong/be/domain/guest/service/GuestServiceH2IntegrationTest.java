package kkagong.be.domain.guest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import javax.sql.DataSource;
import kkagong.be.domain.guest.dto.request.GuestSignUpRequest;
import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import kkagong.be.domain.guest.repository.GuestRepository;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(showSql = false, properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.highlight_sql=false"
})
@AutoConfigureTestDatabase(replace = Replace.ANY)
@ActiveProfiles("test")
@Import({GuestService.class, SessionTokenGenerator.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GuestServiceH2IntegrationTest {

  @Autowired
  private GuestService guestService;

  @Autowired
  private GuestRepository guestRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private DataSource dataSource;

  @MockitoSpyBean
  private SessionTokenGenerator sessionTokenGenerator;

  @BeforeEach
  void prepareDatabase() throws SQLException {
    clearTestDatabase();
  }

  @AfterEach
  void cleanDatabase() throws SQLException {
    clearTestDatabase();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("profileSelections")
  @DisplayName("프로필 선택·미선택 8조합을 실제 H2에 커밋하고 JDBC로 다시 읽어 검증한다")
  void commitsGuestWithTokenHashAndResolvedProfile(
      String scenario,
      GuestSignUpRequest request,
      String expectedNickname,
      CharacterType expectedCharacterType,
      DrinkType expectedDrinkType) {
    String token = guestService.guestSignUp(request);

    assertThat(token).matches("[0-9a-f]{64}");
    assertThat(guestCount()).isEqualTo(1);
    String expectedHash = sessionTokenGenerator.hashForLookup(token);

    // 테스트 바깥 트랜잭션 없이 서비스가 커밋한 행을 JDBC로 읽어 JPA 캐시와 구분한다.
    StoredGuest storedGuest = findGuestByHash(expectedHash);
    assertThat(storedGuest.guestId()).isPositive();
    assertThat(storedGuest.nickname()).isEqualTo(expectedNickname);
    assertThat(storedGuest.characterType()).isEqualTo(expectedCharacterType.name());
    assertThat(storedGuest.drinkType()).isEqualTo(expectedDrinkType.name());
    assertThat(storedGuest.authTokenHash())
        .isEqualTo(expectedHash)
        .matches("[0-9a-f]{64}")
        .isNotEqualTo(token);
    assertThat(storedGuest.lastSeenAt()).isNotNull();
    assertThat(storedGuest.expiresAt()).isEqualTo(storedGuest.lastSeenAt().plusDays(30));
    assertThat(storedGuest.createdAt()).isNotNull();
    assertThat(storedGuest.lastModifiedAt()).isNotNull();
  }

  private static Stream<Arguments> profileSelections() {
    return Stream.of(
        Arguments.of("전체 미선택",
            new GuestSignUpRequest(null, null, null),
            "까공이", CharacterType.Default_Male, DrinkType.Americano_ICE),
        Arguments.of("닉네임만 선택",
            new GuestSignUpRequest("선택닉네임", null, null),
            "선택닉네임", CharacterType.Default_Male, DrinkType.Americano_ICE),
        Arguments.of("캐릭터만 선택",
            new GuestSignUpRequest(null, CharacterType.Default_Female, null),
            "까공이", CharacterType.Default_Female, DrinkType.Americano_ICE),
        Arguments.of("음료만 선택",
            new GuestSignUpRequest(null, null, DrinkType.CafeLatte_ICE),
            "까공이", CharacterType.Default_Male, DrinkType.CafeLatte_ICE),
        Arguments.of("닉네임·캐릭터 선택",
            new GuestSignUpRequest("선택닉네임", CharacterType.Default_Female, null),
            "선택닉네임", CharacterType.Default_Female, DrinkType.Americano_ICE),
        Arguments.of("닉네임·음료 선택",
            new GuestSignUpRequest("선택닉네임", null, DrinkType.CafeLatte_ICE),
            "선택닉네임", CharacterType.Default_Male, DrinkType.CafeLatte_ICE),
        Arguments.of("캐릭터·음료 선택",
            new GuestSignUpRequest(null, CharacterType.Default_Female, DrinkType.CafeLatte_ICE),
            "까공이", CharacterType.Default_Female, DrinkType.CafeLatte_ICE),
        Arguments.of("전체 선택",
            new GuestSignUpRequest(
                "선택닉네임", CharacterType.Default_Female, DrinkType.CafeLatte_ICE),
            "선택닉네임", CharacterType.Default_Female, DrinkType.CafeLatte_ICE)
    );
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nicknamesWithSurroundingWhitespace")
  @DisplayName("앞뒤 공백을 제거한 닉네임을 실제 H2에 커밋한다")
  void commitsGuestWithTrimmedNickname(
      String scenario, String nickname, String expectedNickname) {
    String token = guestService.guestSignUp(new GuestSignUpRequest(nickname, null, null));

    assertThat(guestCount()).isEqualTo(1);
    StoredGuest storedGuest = findGuestByHash(sessionTokenGenerator.hashForLookup(token));
    assertThat(storedGuest.nickname()).isEqualTo(expectedNickname);
  }

  private static Stream<Arguments> nicknamesWithSurroundingWhitespace() {
    return Stream.of(
        Arguments.of("앞뒤 공백 제거", " 까공 ", "까공"),
        Arguments.of("공백 제거 후 길이 상한 12자", " 123456789012 ", "123456789012"),
        Arguments.of("닉네임 내부 공백 유지", " 공부 하는 사람 ", "공부 하는 사람")
    );
  }

  @ParameterizedTest
  @EmptySource
  @ValueSource(strings = {" ", "   ", "\t\n"})
  @DisplayName("첫 생성의 빈 문자열이나 공백뿐인 닉네임은 기본 닉네임으로 실제 H2에 커밋한다")
  void commitsDefaultNicknameForBlankInput(String nickname) {
    GuestSignUpRequest request = new GuestSignUpRequest(
        nickname, CharacterType.Default_Female, DrinkType.Americano_ICE);

    String token = guestService.guestSignUp(request);

    assertThat(guestCount()).isEqualTo(1);
    StoredGuest storedGuest = findGuestByHash(sessionTokenGenerator.hashForLookup(token));
    assertThat(storedGuest.nickname()).isEqualTo("까공이");
    assertThat(storedGuest.characterType()).isEqualTo(CharacterType.Default_Female.name());
    assertThat(storedGuest.drinkType()).isEqualTo(DrinkType.Americano_ICE.name());
  }

  @Test
  @DisplayName("같은 닉네임으로 두 번 가입하면 서로 다른 게스트와 토큰 해시가 저장된다")
  void separateSignUpsPersistSeparateGuests() {
    GuestSignUpRequest request = new GuestSignUpRequest(
        "같은닉네임", CharacterType.Default_Female, DrinkType.CafeLatte_ICE);

    String firstToken = guestService.guestSignUp(request);
    String secondToken = guestService.guestSignUp(request);

    assertThat(firstToken).isNotEqualTo(secondToken);
    assertThat(guestCount()).isEqualTo(2);
    StoredGuest firstGuest = findGuestByHash(sessionTokenGenerator.hashForLookup(firstToken));
    StoredGuest secondGuest = findGuestByHash(sessionTokenGenerator.hashForLookup(secondToken));
    assertThat(firstGuest.guestId()).isNotEqualTo(secondGuest.guestId());
    assertThat(firstGuest.nickname()).isEqualTo(secondGuest.nickname());
    assertThat(firstGuest.authTokenHash()).isNotEqualTo(secondGuest.authTokenHash());
  }

  @Test
  @DisplayName("닉네임 검증 실패 시 실제 DB에 게스트가 남지 않는다")
  void invalidNicknameDoesNotInsertGuest() {
    GuestSignUpRequest request = new GuestSignUpRequest("a".repeat(13), null, null);

    assertThatThrownBy(() -> guestService.guestSignUp(request))
        .isInstanceOfSatisfying(ErrorException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NICKNAME_TOO_LONG));
    assertThat(guestCount()).isZero();
  }

  @Test
  @DisplayName("토큰 발급 실패 시 실제 DB에 게스트가 남지 않는다")
  void tokenGenerationFailureDoesNotInsertGuest() {
    IllegalStateException failure = new IllegalStateException("토큰 발급 실패");
    doThrow(failure).when(sessionTokenGenerator).generate();

    assertThatThrownBy(
        () -> guestService.guestSignUp(new GuestSignUpRequest(null, null, null)))
        .isSameAs(failure);
    assertThat(guestCount()).isZero();
  }

  @Test
  @DisplayName("해시 계산 실패 시 실제 DB에 게스트가 남지 않는다")
  void tokenHashingFailureDoesNotInsertGuest() {
    IllegalStateException failure = new IllegalStateException("해시 계산 실패");
    doThrow(failure).when(sessionTokenGenerator).hashForLookup(anyString());

    assertThatThrownBy(
        () -> guestService.guestSignUp(new GuestSignUpRequest(null, null, null)))
        .isSameAs(failure);
    assertThat(guestCount()).isZero();
  }

  @Test
  @DisplayName("토큰 해시 UNIQUE 제약으로 저장이 실패하면 두 번째 가입은 롤백되고 기존 행은 유지된다")
  void duplicateTokenHashRollsBackSecondSignUp() {
    String fixedToken = "a".repeat(64);
    doReturn(fixedToken).when(sessionTokenGenerator).generate();
    GuestSignUpRequest firstRequest = new GuestSignUpRequest(
        "첫게스트", CharacterType.Default_Male, DrinkType.Americano_ICE);
    String firstToken = guestService.guestSignUp(firstRequest);
    String hash = sessionTokenGenerator.hashForLookup(firstToken);
    StoredGuest original = findGuestByHash(hash);

    assertThatThrownBy(() -> guestService.guestSignUp(
        new GuestSignUpRequest("두번째게스트", CharacterType.Default_Female, DrinkType.CafeLatte_ICE)))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThat(guestCount()).isEqualTo(1);
    assertThat(findGuestByHash(hash)).isEqualTo(original);
  }

  private long guestCount() {
    return jdbcTemplate.queryForObject("select count(*) from guest", Long.class);
  }

  private StoredGuest findGuestByHash(String hash) {
    return jdbcTemplate.queryForObject("""
        select guest_id, nickname, character_type, drink_type, auth_token_hash,
               last_seen_at, expires_at, created_at, last_modified_at
        from guest
        where auth_token_hash = ?
        """, (resultSet, rowNumber) -> new StoredGuest(
        resultSet.getLong("guest_id"),
        resultSet.getString("nickname"),
        resultSet.getString("character_type"),
        resultSet.getString("drink_type"),
        resultSet.getString("auth_token_hash"),
        resultSet.getObject("last_seen_at", LocalDateTime.class),
        resultSet.getObject("expires_at", LocalDateTime.class),
        resultSet.getObject("created_at", LocalDateTime.class),
        resultSet.getObject("last_modified_at", LocalDateTime.class)
    ), hash);
  }

  private void clearTestDatabase() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
    }
    guestRepository.deleteAllInBatch();
  }

  private record StoredGuest(
      long guestId,
      String nickname,
      String characterType,
      String drinkType,
      String authTokenHash,
      LocalDateTime lastSeenAt,
      LocalDateTime expiresAt,
      LocalDateTime createdAt,
      LocalDateTime lastModifiedAt) {
  }
}
