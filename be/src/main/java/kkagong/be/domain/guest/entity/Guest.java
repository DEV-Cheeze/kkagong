package kkagong.be.domain.guest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import kkagong.be.domain.guest.enums.CharacterType;
import kkagong.be.domain.guest.enums.DrinkType;
import kkagong.be.global.entity.BaseEntity;
import kkagong.be.global.exception.ErrorCode;
import kkagong.be.global.exception.ErrorException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "guest")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guest extends BaseEntity {

  @Id
  @SequenceGenerator(name = "guestIdGenerator",
      sequenceName = "guest_seq")
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "guestIdGenerator")
  @Column(name = "guestId")
  private Long guestId;

  @OneToOne(fetch = FetchType.LAZY, mappedBy = "guest")
  private GuestSetting guestSetting;

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "guest")
  private List<GuestSoundSetting> guestSoundSettings = new ArrayList<>();

  @Column(name = "auth_token_hash", unique = true, nullable = false)
  private String authTokenHash;

  @Column(name = "nickname", nullable = false)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(name = "character_type", nullable = false)
  private CharacterType characterType;

  @Enumerated(EnumType.STRING)
  @Column(name = "drink_type", nullable = false)
  private DrinkType drinkType;

  @Column(name = "current_todo_id")
  private Integer currentTodoId;

  @Column(name = "last_seen_at")
  private LocalDateTime lastSeenAt = LocalDateTime.now();

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  @Builder
  public Guest(String nickname, CharacterType characterType, DrinkType drinkType) {
    this.nickname = nickname;
    this.characterType = characterType;
    this.drinkType = drinkType;
    this.expiresAt = lastSeenAt.plusDays(30);
  }


  public static Guest create(CharacterType characterType, String nickname, DrinkType drinkType){

    characterType = characterType != null ? characterType : CharacterType.Default_Male;
    nickname = (nickname == null || nickname.isBlank()) ? "까공이" : validateNickname(nickname);
    drinkType = drinkType != null ? drinkType : DrinkType.Americano_ICE;

    return Guest.builder()
        .characterType(characterType)
        .nickname(nickname)
        .drinkType(drinkType)
        .build();
  }

  public void assignToken(String authTokenHash) {
    this.authTokenHash = authTokenHash;
  }

  private static String validateNickname(String nickname) {
    if (nickname.length() > 12) {
      throw new ErrorException(ErrorCode.NICKNAME_TOO_LONG);
    }
    return nickname;
  }

  public void updateGuestInfo(CharacterType characterType, String nickname, DrinkType drinkType){
    this.characterType = characterType != null ? characterType : this.characterType;
    this.nickname = (nickname == null || nickname.isBlank()) ? this.nickname : validateNickname(nickname);
    this.drinkType = drinkType != null ? drinkType : this.drinkType;
  }

  


}
