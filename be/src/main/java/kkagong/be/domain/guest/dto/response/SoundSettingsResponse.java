package kkagong.be.domain.guest.dto.response;

import kkagong.be.domain.guest.enums.SoundCodes;

public record SoundSettingsResponse(

    SoundCodes soundName,
    int volume,
    boolean enabled

) {

}
