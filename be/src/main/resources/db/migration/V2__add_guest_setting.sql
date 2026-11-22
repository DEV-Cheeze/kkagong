CREATE TABLE guest_setting (
    guest_id BIGINT PRIMARY KEY REFERENCES guest(guest_id) ON DELETE CASCADE, -- 게스트당 설정 최대 한 행
    focus_minutes INTEGER NOT NULL DEFAULT 25,           -- 집중 시간(분)
    short_break_minutes INTEGER NOT NULL DEFAULT 5,      -- 일반 휴식 시간(분)
    long_break_minutes INTEGER NOT NULL DEFAULT 15,      -- 긴 휴식 시간(분)
    auto_transition BOOLEAN NOT NULL DEFAULT FALSE,      -- 구간 종료 후 다음 구간 자동 시작 여부
    share_current_task BOOLEAN NOT NULL DEFAULT TRUE,    -- 현재 할 일 제목 공개 여부
    preferred_work_pose TEXT NOT NULL DEFAULT 'TYPING',  -- 집중 중 표시할 캐릭터 자세 코드
    end_sound_enabled BOOLEAN NOT NULL DEFAULT TRUE,     -- 타이머 종료음 재생 여부
    master_muted BOOLEAN NOT NULL DEFAULT FALSE,          -- 환경음과 종료음 전체 음소거 여부
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, -- 게스트 생성 시각
    last_modified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP -- 게스트 최근 정보 변경 시각
);