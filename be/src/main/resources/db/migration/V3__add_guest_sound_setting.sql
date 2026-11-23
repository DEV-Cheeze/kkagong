CREATE TABLE guest_sound_setting (
    guest_id BIGINT NOT NULL REFERENCES guest(guest_id) ON DELETE CASCADE, -- 설정의 소유 게스트
    sound_code TEXT NOT NULL,                            -- 제공하는 사운드의 코드(예: KEYBOARD)
    volume INTEGER NOT NULL DEFAULT 50 CHECK (volume BETWEEN 0 AND 100), -- 개별 음량
    enabled BOOLEAN NOT NULL DEFAULT FALSE,             -- 개별 사운드를 켤지 여부
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 게스트 생성 시각
    last_modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 게스트 최근 정보 변경 시각
    PRIMARY KEY (guest_id, sound_code)                    -- 게스트와 소리 조합마다 설정 한 행
);
