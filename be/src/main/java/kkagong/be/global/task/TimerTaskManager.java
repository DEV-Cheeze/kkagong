package kkagong.be.global.task;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimerTaskManager {

  @Qualifier("taskScheduler")
  private final TaskScheduler taskScheduler;

  private final ConcurrentMap<Long, EndTask> endTasks =
      new ConcurrentHashMap<>();

  public void scheduleEndTask(Long guestId, Instant endsAt, Runnable onEnd) {
    UUID reservationId = UUID.randomUUID();

    ScheduledFuture<?> task = taskScheduler.schedule(
        () -> {
          try {
            onEnd.run();
          } finally {
            // 종료 중에 다음 구간을 예약할 수 있으므로 이 예약만 정리한다.
            removeCompletedEndTask(guestId, reservationId);
          }
        },
        endsAt
    );

    // 게스트별로 최신 예약 하나를 보관한다. 실행에 들어간 이전 콜백은 구간을 다시 확인한다.
    EndTask previous = endTasks.put(guestId, new EndTask(reservationId, task));
    if (previous != null) {
      previous.future().cancel(false);
    }
  }

  public void cancelEndTask(Long guestId) {
    EndTask task = endTasks.remove(guestId);
    if(task != null) {
      task.future().cancel(false);
    }
  }

  private void removeCompletedEndTask(Long guestId, UUID reservationId) {
    endTasks.computeIfPresent(guestId, (id, task) ->
        task.reservationId().equals(reservationId) ? null : task); //key 없으면 실행 x, 있으면 람다 실행, 반환 null일 경우 삭제
  }

  private record EndTask(UUID reservationId, ScheduledFuture<?> future) {
  }
}
