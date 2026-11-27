package kkagong.be.domain.guest.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import kkagong.be.domain.guest.dto.GuestProfile;
import kkagong.be.domain.guest.dto.request.GuestSignUpRequest;
import kkagong.be.domain.guest.dto.request.ProfileUpdateRequest;
import kkagong.be.domain.guest.dto.request.SoundSettingsRequest;
import kkagong.be.domain.guest.dto.request.TimerSettingsRequest;
import kkagong.be.domain.guest.dto.response.GuestMeResponse;
import kkagong.be.domain.guest.dto.response.SoundInfoResponse;
import kkagong.be.domain.guest.dto.response.SoundSettingsResponse;
import kkagong.be.domain.guest.dto.response.TimerSettingsResponse;
import kkagong.be.domain.guest.enums.SoundCodes;
import kkagong.be.domain.guest.service.GuestService;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import kkagong.be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guests")
public class GuestController {

  private static final String GUEST_TOKEN_COOKIE_NAME = "guestId";

  private final GuestService guestService;

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void guestSignUp(
      HttpServletResponse response,
      @RequestBody @Valid GuestSignUpRequest request) {

    String token = guestService.guestSignUp(request);
    addGuestCookie(response, token);
  }

  @PostMapping("/me")
  public ApiResponse<GuestMeResponse> me(
      @AuthenticationPrincipal Long guestId,
      @CookieValue(name = GUEST_TOKEN_COOKIE_NAME, required = false) String token,
      HttpServletResponse response) {

    GuestMeResponse guestMeResponse = guestService.getMeAndRenewVisit(guestId);
    addGuestCookie(response, token);
    return ApiResponse.ok(guestMeResponse);
  }

  @PatchMapping("/me/profile")
  public ApiResponse<GuestProfile> updateProfile(
      @AuthenticationPrincipal Long guestId,
      @RequestBody @Valid ProfileUpdateRequest request
  ){

    GuestProfile response = guestService.updateGuestProfile(guestId, request);

    return ApiResponse.ok(response);
  }

  @PatchMapping("/me/sound-settings/{soundCode}")
  public ApiResponse<SoundSettingsResponse> soundSettings(
      @AuthenticationPrincipal Long guestId,
      @PathVariable SoundCodes soundCode,
      @RequestBody @Valid SoundSettingsRequest request) {

    SoundSettingsResponse response = guestService.updateSoundSettings(guestId, soundCode, request);

    return ApiResponse.ok(response);
  }

  @GetMapping("/me/sound-settings")
  public ApiResponse<List<SoundInfoResponse>> soundSettings(
      @AuthenticationPrincipal Long guestId) {

    List<SoundInfoResponse> response = guestService.getSoundInfo(guestId);

    return ApiResponse.ok(response);
  }

  @PatchMapping("/me/timer-settings")
  public ApiResponse<TimerSettingsResponse> updateTimerSettings(
      @AuthenticationPrincipal Long guestId,
      @RequestBody @Valid TimerSettingsRequest request) {

    // 쿠키로 인증된 게스트의 타이머 설정을 변경한다.
    TimerSettingsResponse response = guestService.updateTimerSettings(guestId, request);
    return ApiResponse.ok(response);
  }

  private void addGuestCookie(HttpServletResponse response, String token) {
    Cookie cookie = new Cookie(GUEST_TOKEN_COOKIE_NAME, token);
    cookie.setMaxAge(30 * 24 * 60 * 60);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setAttribute("SameSite", "Lax");

    response.addCookie(cookie);
  }
}
