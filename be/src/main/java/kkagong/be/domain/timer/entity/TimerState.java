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
    TimerDurations appliedDurations, // 집중과 그에 이어지는 휴식에 사용할 길이
    int completedFocusCount // 현재 주기에서 자연 종료하거나 스킵한 집중 횟수
) {

  public boolean isDue(Instant now) {
    return status == Status.RUNNING && !now.isBefore(endsAt);
  }

  public TimerState start(UUID newIntervalId, Instant startedAt) {
    long lengthMs = appliedDurations.durationMs(phase);
    // 새 실행 구간을 만들어도 이미 완료한 집중 횟수는 이어간다.
    return new TimerState(newIntervalId, phase, Status.RUNNING, lengthMs, startedAt,
        startedAt.plusMillis(lengthMs), null, appliedDurations, completedFocusCount);
  }

  public enum Phase {
    FOCUS, SHORT_BREAK, LONG_BREAK
  }

  public enum Status {
    READY, RUNNING, PAUSED
  }
}
