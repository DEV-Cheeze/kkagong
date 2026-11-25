package kkagong.be.domain.timer.dto.response;

import java.time.Instant;
import java.util.UUID;
import kkagong.be.domain.timer.entity.TimerState;
import lombok.Builder;

@Builder
public record TimerInfoResponse(
    UUID intervalId,
    TimerState.Phase phase,
    TimerState.Status status,
    long durationMs,
    Instant endsAt,
    Instant serverNow,
    Long remainingMs
) {

  public static TimerInfoResponse from(TimerState state, Instant serverNow) {
    return TimerInfoResponse.builder()
        .intervalId(state.intervalId())
        .phase(state.phase())
        .status(state.status())
        .durationMs(state.durationMs())
        .endsAt(state.endsAt())
        .serverNow(serverNow)
        .remainingMs(state.pausedRemainingMs())
        .build();
  }
}
