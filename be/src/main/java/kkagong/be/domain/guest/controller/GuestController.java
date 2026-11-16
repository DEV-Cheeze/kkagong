package kkagong.be.domain.guest.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import kkagong.be.domain.guest.dto.request.GuestSignUpRequest;
import kkagong.be.domain.guest.entity.Guest;
import kkagong.be.domain.guest.service.GuestService;
import kkagong.be.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guests")
public class GuestController {


  private final GuestService guestService;

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void guestSignUp(
      HttpServletResponse response,
      @RequestBody @Valid GuestSignUpRequest request) {

    String token = guestService.guestSignUp(request);

    Cookie cookie = new Cookie("guestId", token);
    cookie.setMaxAge(3600 * 24 * 30);
    cookie.setPath("/");
    cookie.setHttpOnly(true);
    cookie.setSecure(true);
    cookie.setAttribute("SameSite", "Lax");

    response.addCookie(cookie);
  }

}
