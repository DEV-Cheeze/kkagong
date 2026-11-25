package kkagong.be.domain.timer.service;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import kkagong.be.domain.timer.entity.TimerState;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTimerStore {

  private final ConcurrentMap<Long, TimerState> states = new ConcurrentHashMap<>();

  public Optional<TimerState> find(Long guestId) { // 현재 메모리에 실행된 사용자 타이머 찾기
    return Optional.ofNullable(states.get(guestId));
  }

  public TimerState update(Long guestId, Function<Optional<TimerState>, TimerState> change) { // 사용자 타이머 업데이트
    return states.compute(guestId, (id, current) ->
        Objects.requireNonNull(change.apply(Optional.ofNullable(current))));
  }

  public Optional<TimerState> updateIfPresent(Long guestId, UnaryOperator<TimerState> change) {
    return Optional.ofNullable(states.computeIfPresent(guestId, (id, current) ->
        Objects.requireNonNull(change.apply(current))));
  }

}
