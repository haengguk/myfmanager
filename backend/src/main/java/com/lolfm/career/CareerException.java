package com.lolfm.career;

/** Stable application failure; HTTP mapping stays in the controller boundary. */
public final class CareerException extends RuntimeException {
    public enum Type {
        REQUEST_INVALID,
        NOT_FOUND,
        MANAGED_TEAM_NOT_FOUND,
        COMMAND_CONFLICT,
        CAPACITY_REACHED,
        COMMAND_RECEIPT_INTEGRITY_FAILURE,
        LINKED_SEASON_INTEGRITY_FAILURE,
        RESOURCE_INTEGRITY_FAILURE,
        CALENDAR_NOT_FOUND,
        CALENDAR_STALE_REVISION,
        CALENDAR_COMMAND_CONFLICT,
        CALENDAR_ADVANCE_ALREADY_PENDING,
        CALENDAR_LEGACY_PENDING_RECONCILIATION_REQUIRED,
        CALENDAR_COMMAND_INTEGRITY_FAILURE,
        CALENDAR_MIGRATION_REQUIRED,
        CALENDAR_INTEGRITY_FAILURE,
        CALENDAR_BACKGROUND_UNAVAILABLE,
        COMPETITION_STALE_REVISION,
        COMPETITION_COMMAND_CONFLICT,
        COMPETITION_BACKGROUND_UNAVAILABLE
    }

    private final Type type;
    private final String field;
    private final String clientMessage;

    private CareerException(
            Type type,
            String field,
            String clientMessage,
            Throwable cause
    ) {
        super(type.name(), cause);
        this.type = type;
        this.field = field;
        this.clientMessage = clientMessage;
    }

    public static CareerException invalid(String field, String message) {
        return new CareerException(Type.REQUEST_INVALID, field, message, null);
    }

    public static CareerException notFound() {
        return new CareerException(Type.NOT_FOUND, "careerId",
                "요청한 Career를 찾을 수 없습니다.", null);
    }

    public static CareerException teamNotFound() {
        return new CareerException(Type.MANAGED_TEAM_NOT_FOUND, "managedTeamCode",
                "현재 LCK 주전 catalog에 없는 관리 팀입니다.", null);
    }

    public static CareerException commandConflict() {
        return new CareerException(Type.COMMAND_CONFLICT, "clientCommandId",
                "clientCommandId가 기존 Career 생성 요청과 충돌합니다.", null);
    }

    public static CareerException capacityReached() {
        return new CareerException(Type.CAPACITY_REACHED, null,
                "Career 저장 슬롯은 최대 100개입니다. 기존 저장은 계속 불러올 수 있습니다.",
                null);
    }

    public static CareerException commandReceiptIntegrity() {
        return new CareerException(Type.COMMAND_RECEIPT_INTEGRITY_FAILURE, null,
                "Career 생성 명령의 저장 무결성을 확인할 수 없습니다.", null);
    }

    public static CareerException linkedSeasonIntegrity() {
        return linkedSeasonIntegrity(null);
    }

    public static CareerException linkedSeasonIntegrity(Throwable cause) {
        return new CareerException(Type.LINKED_SEASON_INTEGRITY_FAILURE, null,
                "Career와 연결된 League Season의 무결성을 확인할 수 없습니다.", cause);
    }

    public static CareerException resourceIntegrity() {
        return new CareerException(Type.RESOURCE_INTEGRITY_FAILURE, null,
                "Career 생성 또는 복원에 필요한 resource 무결성을 확인할 수 없습니다.", null);
    }

    public static CareerException calendarNotFound() {
        return new CareerException(Type.CALENDAR_NOT_FOUND, null,
                "Career 캘린더 상태를 찾을 수 없습니다.", null);
    }

    public static CareerException calendarStaleRevision() {
        return new CareerException(Type.CALENDAR_STALE_REVISION,
                "expectedCalendarRevision",
                "캘린더 revision이 변경되었습니다. 최신 상태를 다시 불러오세요.", null);
    }

    public static CareerException calendarCommandConflict() {
        return new CareerException(Type.CALENDAR_COMMAND_CONFLICT, "clientCommandId",
                "clientCommandId가 기존 캘린더 진행 요청과 충돌합니다.", null);
    }

    public static CareerException calendarAdvanceAlreadyPending() {
        return new CareerException(Type.CALENDAR_ADVANCE_ALREADY_PENDING,
                "clientCommandId",
                "이 Career에는 완료되지 않은 캘린더 진행 요청이 있습니다.", null);
    }

    public static CareerException calendarLegacyPendingReconciliationRequired() {
        return new CareerException(
                Type.CALENDAR_LEGACY_PENDING_RECONCILIATION_REQUIRED,
                "clientCommandId",
                "기존 날짜 진행 요청의 원본 mode와 revision을 증명할 수 없어 새 진행을 차단했습니다.",
                null);
    }

    public static CareerException calendarCommandIntegrity() {
        return new CareerException(Type.CALENDAR_COMMAND_INTEGRITY_FAILURE, null,
                "캘린더 진행 명령의 저장 무결성을 확인할 수 없습니다.", null);
    }

    public static CareerException calendarMigrationRequired() {
        return new CareerException(Type.CALENDAR_MIGRATION_REQUIRED, null,
                "이 Career는 캘린더 상태 마이그레이션 확인이 필요합니다.", null);
    }

    public static CareerException calendarIntegrity(Throwable cause) {
        return new CareerException(Type.CALENDAR_INTEGRITY_FAILURE, null,
                "Career 캘린더 또는 연결된 일정의 무결성을 확인할 수 없습니다.", cause);
    }

    public static CareerException calendarBackgroundUnavailable() {
        return new CareerException(Type.CALENDAR_BACKGROUND_UNAVAILABLE, null,
                "경기 작업은 저장되었지만 계산 worker를 깨우지 못했습니다. 같은 요청으로 다시 시도해 주세요.",
                null);
    }

    public static CareerException competitionStaleRevision() {
        return new CareerException(Type.COMPETITION_STALE_REVISION,
                "expectedCompetitionRevision",
                "대회 revision이 변경되었습니다. 최신 캘린더를 다시 불러오세요.", null);
    }

    public static CareerException competitionCommandConflict() {
        return new CareerException(Type.COMPETITION_COMMAND_CONFLICT,
                "clientCommandId",
                "현재 대회 경기 상태와 명령이 충돌합니다. 최신 상태를 다시 불러오세요.", null);
    }

    public static CareerException competitionBackgroundUnavailable() {
        return new CareerException(Type.COMPETITION_BACKGROUND_UNAVAILABLE, null,
                "대회 경기 작업은 저장되었지만 계산 worker를 깨우지 못했습니다. 같은 요청으로 다시 시도해 주세요.",
                null);
    }

    public Type type() { return type; }
    public String field() { return field; }
    public String clientMessage() { return clientMessage; }
}
