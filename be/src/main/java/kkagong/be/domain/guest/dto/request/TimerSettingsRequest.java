package kkagong.be.domain.guest.dto.request;

import org.hibernate.validator.constraints.Range;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

// PATCH에서 생략한 항목은 null로 받아 기존 설정을 사용한다.
public record TimerSettingsRequest(
    @Range(min = 1, max = 180, message = "집중 시간은 1분 이상 180분 이하여야 합니다.")
    @JsonDeserialize(using = MinutesDeserializer.class)
    Integer focusMinutes,

    @Range(min = 1, max = 180, message = "일반 휴식 시간은 1분 이상 180분 이하여야 합니다.")
    @JsonDeserialize(using = MinutesDeserializer.class)
    Integer shortBreakMinutes,

    @Range(min = 1, max = 180, message = "긴 휴식 시간은 1분 이상 180분 이하여야 합니다.")
    @JsonDeserialize(using = MinutesDeserializer.class)
    Integer longBreakMinutes,

    Boolean autoTransition
) {

  static final class MinutesDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
      // 분 단위 정수만 받는다. 소수와 숫자 문자열의 자동 변환을 막는다.
      if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
        return context.reportInputMismatch(Integer.class, "타이머 시간은 정수 분으로 입력해야 합니다.");
      }
      return parser.getIntValue();
    }
  }
}
