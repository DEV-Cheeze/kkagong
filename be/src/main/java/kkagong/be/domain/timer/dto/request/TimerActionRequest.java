package kkagong.be.domain.timer.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import kkagong.be.domain.timer.entity.TimerState;
import kkagong.be.domain.timer.enums.TimerAction;

public record TimerActionRequest(
    @NotNull TimerAction action,
    UUID intervalId
) {

}
