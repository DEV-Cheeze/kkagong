package kkagong.be.domain.guest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class SessionTokenGeneratorTest {

  @Test
  @DisplayName("실제 난수로 반복 발급한 토큰은 64자리 소문자 16진수이며 표본 내 중복이 없다")
  void generatesDistinctHexTokens() {
    SessionTokenGenerator generator = new SessionTokenGenerator();

    List<String> tokens = Stream.generate(generator::generate).limit(100).toList();

    assertThat(tokens)
        .allSatisfy(token -> assertThat(token).matches("[0-9a-f]{64}"))
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("발급할 때마다 SecureRandom에서 새 32바이트를 받아 토큰에 반영한다")
  void usesFresh32ByteRandomInputForEveryToken() {
    AtomicInteger randomCalls = new AtomicInteger();
    try (MockedConstruction<SecureRandom> ignored = mockConstruction(SecureRandom.class,
        (random, context) -> doAnswer(invocation -> {
          byte[] bytes = invocation.getArgument(0);
          assertThat(bytes).hasSize(32);
          int firstByte = randomCalls.getAndIncrement() * 32;
          for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (firstByte + i);
          }
          return null;
        }).when(random).nextBytes(any(byte[].class)))) {
      SessionTokenGenerator generator = new SessionTokenGenerator();

      String firstToken = generator.generate();
      String secondToken = generator.generate();

      assertThat(firstToken)
          .isEqualTo("630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd");
      assertThat(secondToken)
          .isEqualTo("72dbb7336c76780023f83da4c355f2eeea85733b13d3477697917790c1229084");
      assertThat(randomCalls).hasValue(2);
    }
  }

  @ParameterizedTest(name = "입력 [{0}]의 조회용 해시")
  @MethodSource("lookupHashVectors")
  @DisplayName("조회용 해시는 입력 문자열의 UTF-8 바이트에 대한 SHA-256 고정 벡터와 일치한다")
  void hashesExactUtf8TokenWithoutNormalization(String token, String expectedHash) {
    SessionTokenGenerator generator = new SessionTokenGenerator();

    String firstHash = generator.hashForLookup(token);
    String repeatedHash = generator.hashForLookup(token);

    assertThat(firstHash).isEqualTo(expectedHash);
    assertThat(repeatedHash).isEqualTo(expectedHash);
  }

  private static Stream<Arguments> lookupHashVectors() {
    return Stream.of(
        Arguments.of("abc",
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
        Arguments.of("까공이",
            "d8177b3917e4f084ea5a00b40d14f1ad9f74ce3d286a67604cc1d2dda1437875"),
        Arguments.of("AbC",
            "482e1ae75acdf731feeddc4875fd0fc05ea436a3d751506163989e998ab1038d"),
        Arguments.of("abc ",
            "5488613c42b0d34d60f7aa9e94be317a3ee102a2bbd91ccc73cc79fbc2269955"),
        Arguments.of("0123456789abcdef".repeat(4),
            "a8ae6e6ee929abea3afcfc5258c8ccd6f85273e0d4626d26c7279f3250f77c8e")
    );
  }

  @Test
  @DisplayName("조회용 토큰이 null이면 현재 구현의 NullPointerException으로 거부한다")
  void rejectsNullToken() {
    SessionTokenGenerator generator = new SessionTokenGenerator();

    assertThatNullPointerException().isThrownBy(() -> generator.hashForLookup(null));
  }

  @Test
  @DisplayName("난수 공급자가 실패하면 원인 예외를 그대로 전파하고 토큰을 반환하지 않는다")
  void propagatesRandomProviderFailure() {
    ProviderException failure = new ProviderException("random source unavailable");
    try (MockedConstruction<SecureRandom> ignored = mockConstruction(SecureRandom.class,
        (random, context) -> doThrow(failure).when(random).nextBytes(any(byte[].class)))) {
      SessionTokenGenerator generator = new SessionTokenGenerator();

      assertThatThrownBy(generator::generate).isSameAs(failure);
    }
  }

  @Test
  @DisplayName("토큰 발급 중 SHA-256을 사용할 수 없으면 원인을 보존한 IllegalStateException을 던진다")
  void wrapsMissingAlgorithmDuringGeneration() {
    NoSuchAlgorithmException failure = new NoSuchAlgorithmException("SHA-256 unavailable");
    try (MockedConstruction<SecureRandom> ignored = mockConstruction(SecureRandom.class);
        MockedStatic<MessageDigest> messageDigest = mockStatic(MessageDigest.class)) {
      messageDigest.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(failure);
      SessionTokenGenerator generator = new SessionTokenGenerator();

      assertThatThrownBy(generator::generate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("SHA-256 is unavailable")
          .hasCause(failure);
    }
  }

  @Test
  @DisplayName("조회용 해시 계산 중 SHA-256을 사용할 수 없으면 원인을 보존한 IllegalStateException을 던진다")
  void wrapsMissingAlgorithmDuringLookupHashing() {
    SessionTokenGenerator generator = new SessionTokenGenerator();
    NoSuchAlgorithmException failure = new NoSuchAlgorithmException("SHA-256 unavailable");
    try (MockedStatic<MessageDigest> messageDigest = mockStatic(MessageDigest.class)) {
      messageDigest.when(() -> MessageDigest.getInstance("SHA-256")).thenThrow(failure);

      assertThatThrownBy(() -> generator.hashForLookup("token"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("SHA-256 is unavailable")
          .hasCause(failure);
    }
  }
}
