package kkagong.be.domain.timer.entity;

import java.time.Instant;
import java.util.UUID;

public record TimerState(
    UUID intervalId,
    Phase phase, // 타이머 페이즈
    Status status, // 타이머 상태
    long durationMs,
    Instant runningSince,
    Instant endsAt,
    Long pausedRemainingMs
) {
  public enum Phase {
    FOCUS, SHORT_BREAK, LONG_BREAK
  }

  public enum Status {
    READY, RUNNING, PAUSED
  }
}
