package kkagong.be.global.task;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.domain.timer.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimerTaskManager {

  @Qualifier("taskScheduler")
  private final TaskScheduler taskScheduler;
  private final TaskService taskService;

  private final ConcurrentMap<Long, EndTask> endTasks =
      new ConcurrentHashMap<>();

  public void scheduleEndTask(Long guestId, TimerState timer, UUID intervalId) { //작업 예약 및 map에 넣기
    Instant endsAt = timer.endsAt();
    UUID reservationId = UUID.randomUUID();

    ScheduledFuture<?> task = taskScheduler.schedule(
        () -> {
          try {
            taskService.finishIfDue(guestId, intervalId, endsAt);
          } finally {
            removeCompletedEndTask(guestId, reservationId);
          }
        },
        endsAt
    );

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
