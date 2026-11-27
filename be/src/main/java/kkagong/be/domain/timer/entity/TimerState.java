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
    Long pausedRemainingMs,
    TimerDurations appliedDurations // 집중과 그에 이어지는 휴식에 사용할 길이
) {

  public boolean isDue(Instant now) {
    return status == Status.RUNNING && !now.isBefore(endsAt);
  }

  public TimerState start(UUID newIntervalId, Instant startedAt) {
    long lengthMs = appliedDurations.durationMs(phase);
    return new TimerState(newIntervalId, phase, Status.RUNNING, lengthMs, startedAt,
        startedAt.plusMillis(lengthMs), null, appliedDurations);
  }

  public enum Phase {
    FOCUS, SHORT_BREAK, LONG_BREAK
  }

  public enum Status {
    READY, RUNNING, PAUSED
  }
}
