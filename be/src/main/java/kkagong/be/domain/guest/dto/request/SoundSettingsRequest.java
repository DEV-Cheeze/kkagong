package kkagong.be.domain.guest.dto.request;

import org.hibernate.validator.constraints.Range;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SoundSettingsRequest(

    @Range(min = 0, max = 100, message = "음량은 0부터 100까지 입력할 수 있습니다.")
    @JsonDeserialize(using = VolumeDeserializer.class)
    Integer volume,
    Boolean enabled

) {

  static final class VolumeDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
      if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
        return context.reportInputMismatch(Integer.class, "음량은 정수여야 합니다.");
      }
      return parser.getIntValue();
    }
  }
}
