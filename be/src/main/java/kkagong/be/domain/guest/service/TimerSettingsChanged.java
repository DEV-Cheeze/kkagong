package kkagong.be.domain.guest.service;

import kkagong.be.domain.timer.entity.TimerSettings;

public record TimerSettingsChanged(Long guestId, TimerSettings previous) {
}
