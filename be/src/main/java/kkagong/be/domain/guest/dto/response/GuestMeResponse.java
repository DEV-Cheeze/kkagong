package kkagong.be.domain.guest.dto.response;

import kkagong.be.domain.guest.dto.GuestProfile;
import kkagong.be.domain.guest.dto.GuestSettings;

public record GuestMeResponse(

    GuestProfile guestProfile,
    GuestSettings guestSettings

) {

}
