package kkagong.be.domain.guest.dto.response;

import kkagong.be.domain.guest.enums.SoundCodes;

public record SoundInfoResponse(
    SoundCodes soundCodes,
    int volume,
    boolean enabled
) {

}
