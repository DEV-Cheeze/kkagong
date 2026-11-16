CREATE SEQUENCE guest_seq AS BIGINT INCREMENT BY 50 START WITH 1; -- DDL 시퀀스 설정

CREATE TABLE guest (
    guest_id BIGINT DEFAULT nextval('guest_seq') PRIMARY KEY, -- DB가 발급하는 게스트 식별자
    auth_token_hash TEXT NOT NULL UNIQUE,                -- 쿠키의 인증 토큰을 확인할 때 사용하는 해시
    nickname TEXT NOT NULL,                              -- 화면에 표시할 닉네임. 중복 허용
    character_type TEXT NOT NULL,                        -- 사용자가 선택한 캐릭터 코드
    drink_type TEXT NOT NULL,                            -- ICE/HOT 구분을 포함한 음료 코드
    current_todo_id INTEGER,                             -- 현재 선택한 할 일 ID. 미선택이면 NULL, FK 없음
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 게스트 생성 시각
    last_modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 게스트 최근 정보 변경 시각
    last_seen_at TIMESTAMPTZ NOT NULL,                    -- 마지막 정상 방문 시각
    expires_at TIMESTAMPTZ NOT NULL                       -- 인증과 개인 자료의 만료 시각
);