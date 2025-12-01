package com.jun_bank.transfer_service.domain.transfer.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * 이체 상태
 * <p>
 * 이체의 최종 결과 상태를 정의합니다.
 * SAGA 진행 상태와는 별개로, 이체 자체의 성공/실패 여부를 나타냅니다.
 *
 * <h3>상태 전이 규칙:</h3>
 * <pre>
 *              SAGA 성공
 * ┌─────────┐ ─────────▶ ┌─────────┐
 * │ PENDING │            │ SUCCESS │  ← 최종 상태
 * └─────────┘            └─────────┘
 *     │ │
 *     │ │ SAGA 실패 (보상 완료 포함)
 *     │ └───────────────▶ ┌────────┐
 *     │                   │ FAILED │  ← 최종 상태
 *     │                   └────────┘
 *     │
 *     │ 사용자 취소 (PENDING에서만)
 *     └─────────────────▶ ┌───────────┐
 *                         │ CANCELLED │  ← 최종 상태
 *                         └───────────┘
 * </pre>
 *
 * @see Transfer
 * @see SagaStatus
 */
public enum TransferStatus {

    /**
     * 처리 중
     * <p>
     * SAGA가 진행 중인 상태입니다.
     * 이 상태에서만 다른 상태로 전이 가능합니다.
     * </p>
     */
    PENDING("처리중", false),

    /**
     * 성공
     * <p>
     * SAGA가 성공적으로 완료된 최종 상태입니다.
     * 출금과 입금 모두 완료되었습니다.
     * </p>
     */
    SUCCESS("성공", true),

    /**
     * 실패
     * <p>
     * SAGA가 실패한 최종 상태입니다.
     * 보상 트랜잭션이 완료되어 원상 복구되었습니다.
     * </p>
     */
    FAILED("실패", true),

    /**
     * 취소
     * <p>
     * 사용자에 의해 취소된 최종 상태입니다.
     * PENDING 상태에서만 취소 가능합니다.
     * </p>
     */
    CANCELLED("취소", true);

    private final String description;
    private final boolean isFinal;

    TransferStatus(String description, boolean isFinal) {
        this.description = description;
        this.isFinal = isFinal;
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
     * 최종 상태 여부 확인
     *
     * @return 최종 상태이면 true
     */
    public boolean isFinal() {
        return isFinal;
    }

    /**
     * 처리 중 여부 확인
     *
     * @return PENDING이면 true
     */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * 성공 여부 확인
     *
     * @return SUCCESS이면 true
     */
    public boolean isSuccess() {
        return this == SUCCESS;
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
     * 취소 여부 확인
     *
     * @return CANCELLED이면 true
     */
    public boolean isCancelled() {
        return this == CANCELLED;
    }

    /**
     * 취소 가능 여부 확인
     *
     * @return PENDING 상태이면 true
     */
    public boolean isCancellable() {
        return this == PENDING;
    }

    /**
     * 특정 상태로 전환 가능 여부 확인
     *
     * @param target 전환하려는 상태
     * @return 전환 가능하면 true
     */
    public boolean canTransitionTo(TransferStatus target) {
        if (this == target) {
            return false;
        }
        return getAllowedTransitions().contains(target);
    }

    /**
     * 현재 상태에서 전환 가능한 상태 목록 반환
     *
     * @return 전환 가능한 상태 Set
     */
    public Set<TransferStatus> getAllowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(SUCCESS, FAILED, CANCELLED);
            case SUCCESS, FAILED, CANCELLED -> EnumSet.noneOf(TransferStatus.class);
        };
    }
}