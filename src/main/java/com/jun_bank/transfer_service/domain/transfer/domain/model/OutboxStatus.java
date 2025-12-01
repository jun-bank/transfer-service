package com.jun_bank.transfer_service.domain.transfer.domain.model;

/**
 * Outbox 이벤트 상태
 * <p>
 * Outbox 패턴에서 이벤트의 발행 상태를 정의합니다.
 *
 * <h3>상태 전이:</h3>
 * <pre>
 *              발행 성공
 * ┌─────────┐ ─────────▶ ┌──────┐
 * │ PENDING │            │ SENT │  ← 최종 상태
 * └─────────┘            └──────┘
 *     │
 *     │ 재시도 초과
 *     └─────────────────▶ ┌────────┐
 *                         │ FAILED │  ← 최종 상태 (수동 처리 필요)
 *                         └────────┘
 * </pre>
 *
 * @see OutboxEvent
 */
public enum OutboxStatus {

    /**
     * 발행 대기
     * <p>
     * 이벤트가 생성되어 발행을 기다리는 상태.
     * OutboxScheduler가 폴링하여 발행합니다.
     * </p>
     */
    PENDING("발행 대기"),

    /**
     * 발행 완료
     * <p>
     * Kafka로 이벤트가 성공적으로 발행된 상태.
     * 일정 기간 후 삭제 대상.
     * </p>
     */
    SENT("발행 완료"),

    /**
     * 발행 실패
     * <p>
     * 최대 재시도 횟수를 초과하여 실패한 상태.
     * 수동 처리가 필요합니다.
     * </p>
     */
    FAILED("발행 실패");

    private final String description;

    OutboxStatus(String description) {
        this.description = description;
    }

    /**
     * 상태 설명 반환
     *
     * @return 한글 설명
     */
    public String getDescription() {
        return description;
    }

    /**
     * 발행 대기 여부 확인
     *
     * @return PENDING이면 true
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 발행 완료 여부 확인
     *
     * @return SENT이면 true
     */
    public boolean isSent() {
        return this == SENT;
    }

    /**
     * 실패 여부 확인
     *
     * @return FAILED이면 true
     */
    public boolean isFailed() {
        return this == FAILED;
    }

    /**
     * 재시도 가능 여부 확인
     *
     * @return PENDING이면 true
     */
    public boolean isRetryable() {
        return this == PENDING;
    }
}