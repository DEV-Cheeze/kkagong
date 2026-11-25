package kkagong.be.domain.timer.controller;

import jakarta.validation.Valid;
import kkagong.be.domain.timer.dto.request.TimerActionRequest;
import kkagong.be.domain.timer.service.TimerService;
import kkagong.be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/timer")
public class TimerController {

  private final TimerService timerService;

  @PostMapping("/actions")
  public ApiResponse<Void> timerAction(
      @AuthenticationPrincipal Long guestId,
      @RequestBody @Valid TimerActionRequest timerActionRequest) {

    timerService.action(guestId, timerActionRequest);

    return ApiResponse.ok_noContent();
  }


}
