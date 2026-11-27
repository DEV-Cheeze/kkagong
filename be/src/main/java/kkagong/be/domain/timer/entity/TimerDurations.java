package kkagong.be.domain.timer.entity;

import kkagong.be.domain.timer.entity.TimerState.Phase;

// 한 번의 집중과 이어지는 휴식에 적용하는 세 구간의 길이다.
public record TimerDurations(int focusMinutes, int shortBreakMinutes, int longBreakMinutes) {

  public long durationMs(Phase phase) {
    int minutes = switch (phase) {
      case FOCUS -> focusMinutes;
      case SHORT_BREAK -> shortBreakMinutes;
      case LONG_BREAK -> longBreakMinutes;
    };
    return minutes * 60_000L;
  }
}
