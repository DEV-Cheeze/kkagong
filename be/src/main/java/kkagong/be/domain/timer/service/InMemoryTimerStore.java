package kkagong.be.domain.timer.service;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import kkagong.be.domain.timer.entity.TimerState;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTimerStore {

  private final ConcurrentMap<Long, GuestTimer> timers = new ConcurrentHashMap<>();

  public Optional<TimerState> find(Long guestId) { // 현재 메모리에 실행된 사용자 타이머 찾기
    return Optional.ofNullable(timers.get(guestId)).map(timer -> timer.state);
  }

  public TimerState update(Long guestId, Function<Optional<TimerState>, TimerState> change) { // 사용자 타이머 업데이트
    return update(guestId, change, state -> {});
  }

  public TimerState update(Long guestId, Function<Optional<TimerState>, TimerState> change,
      Consumer<TimerState> afterSave) {
    // 게스트별 항목을 락으로 사용한다. 같은 게스트의 모든 변경 경로가 이 항목을 공유한다.
    GuestTimer timer = timers.computeIfAbsent(guestId, id -> new GuestTimer());
    synchronized (timer) {
      TimerState next = change.apply(Optional.ofNullable(timer.state));
      return saveAndNotify(timer, next, afterSave);
    }
  }

  public Optional<TimerState> updateIfPresent(Long guestId, UnaryOperator<TimerState> change) {
    return updateIfPresent(guestId, change, state -> {});
  }

  public Optional<TimerState> updateIfPresent(Long guestId, UnaryOperator<TimerState> change,
      Consumer<TimerState> afterSave) {
    // 조회나 종료 예약은 이미 있는 게스트 타이머만 갱신한다.
    GuestTimer timer = timers.get(guestId);
    if (timer == null) {
      return Optional.empty();
    }
    synchronized (timer) {
      return Optional.ofNullable(timer.state)
          .map(current -> saveAndNotify(timer, change.apply(current), afterSave));
    }
  }

  private TimerState saveAndNotify(GuestTimer timer, TimerState next, Consumer<TimerState> afterSave) {
    // 상태부터 반영하고, 같은 락 안에서 후속 발행을 호출해 다음 변경이 앞서지 못하게 한다.
    timer.state = Objects.requireNonNull(next);
    afterSave.accept(next);
    return next;
  }

  private static class GuestTimer {
    // 읽기 전용 find()는 락 없이 저장된 최신 상태를 조회할 수 있다.
    private volatile TimerState state;
  }

}
