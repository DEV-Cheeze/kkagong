package kkagong.be.domain.guest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class SessionTokenGenerator {

  private final SecureRandom random = new SecureRandom();

  public String generate() { // 클라이언트에 전달
    byte[] randomBytes = new byte[32];
    random.nextBytes(randomBytes);
    return sha256Hex(randomBytes);
  }

  public String hashForLookup(String token) { // DB 저장용으로 전달
    return sha256Hex(token.getBytes(StandardCharsets.UTF_8));
  }

  private static String sha256Hex(byte[] input) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }


}
