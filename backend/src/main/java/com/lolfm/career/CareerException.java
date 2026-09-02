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
        RESOURCE_INTEGRITY_FAILURE
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

    public Type type() { return type; }
    public String field() { return field; }
    public String clientMessage() { return clientMessage; }
}
