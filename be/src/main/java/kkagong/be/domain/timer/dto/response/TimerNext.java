package kkagong.be.domain.timer.dto.response;

import kkagong.be.domain.timer.entity.TimerState.Phase;

// 실행 중인 구간이 자연 종료되면 브라우저가 표시할 다음 구간이다.
public record TimerNext(Phase phase, long durationMs) {
}
