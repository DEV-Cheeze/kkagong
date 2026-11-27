package kkagong.be.domain.timer.entity;

public record TimerSettings(
    int focusMinutes,
    int shortBreakMinutes,
    int longBreakMinutes,
    boolean autoTransition
) {

  public TimerDurations durations() {
    return new TimerDurations(focusMinutes, shortBreakMinutes, longBreakMinutes);
  }
}
