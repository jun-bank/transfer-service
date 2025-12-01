package com.jun_bank.transfer_service.domain.transfer.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * SAGA 상태
 * <p>
 * 이체 SAGA의 진행 상태를 정의합니다.
 * 각 단계별 상태와 보상 트랜잭션 상태를 포함합니다.
 *
 * <h3>정상 흐름:</h3>
 * <pre>
 * STARTED → DEBIT_PENDING → DEBIT_COMPLETED → CREDIT_PENDING → CREDIT_COMPLETED → COMPLETED
 * </pre>
 *
 * <h3>실패 흐름 (출금 실패):</h3>
 * <pre>
 * STARTED → DEBIT_PENDING → DEBIT_FAILED → FAILED
 * </pre>
 *
 * <h3>실패 흐름 (입금 실패 → 보상):</h3>
 * <pre>
 * STARTED → DEBIT_PENDING → DEBIT_COMPLETED → CREDIT_PENDING → CREDIT_FAILED
 *         → COMPENSATING → COMPENSATED → FAILED
 * </pre>
 *
 * <h3>상태 다이어그램:</h3>
 * <pre>
 *                    STARTED
 *                       │
 *                       ▼
 *                 DEBIT_PENDING
 *                    │      │
 *           성공 ◀───┘      └───▶ 실패
 *                    │             │
 *                    ▼             ▼
 *             DEBIT_COMPLETED   DEBIT_FAILED
 *                    │             │
 *                    ▼             ▼
 *              CREDIT_PENDING   FAILED
 *                 │      │
 *        성공 ◀───┘      └───▶ 실패
 *                 │             │
 *                 ▼             ▼
 *          CREDIT_COMPLETED   CREDIT_FAILED
 *                 │             │
 *                 ▼             ▼
 *             COMPLETED     COMPENSATING
 *                               │
 *                               ▼
 *                          COMPENSATED
 *                               │
 *                               ▼
 *                            FAILED
 * </pre>
 *
 * @see Transfer
 * @see TransferStatus
 */
public enum SagaStatus {

    // ========================================
    // 정상 흐름 상태
    // ========================================

    /**
     * SAGA 시작
     * <p>이체 요청이 접수되어 SAGA가 시작된 상태</p>
     */
    STARTED("시작됨", false, false),

    /**
     * 출금 요청 중
     * <p>출금 계좌에서 금액 차감 요청을 보낸 상태</p>
     */
    DEBIT_PENDING("출금 요청중", false, false),

    /**
     * 출금 완료
     * <p>출금 계좌에서 금액이 차감된 상태</p>
     */
    DEBIT_COMPLETED("출금 완료", false, true),

    /**
     * 입금 요청 중
     * <p>입금 계좌에 금액 입금 요청을 보낸 상태</p>
     */
    CREDIT_PENDING("입금 요청중", false, true),

    /**
     * 입금 완료
     * <p>입금 계좌에 금액이 입금된 상태</p>
     */
    CREDIT_COMPLETED("입금 완료", false, false),

    // ========================================
    // 실패 상태
    // ========================================

    /**
     * 출금 실패
     * <p>
     * 출금 처리 중 실패한 상태.
     * 잔액 부족, 계좌 동결 등의 사유.
     * 보상 트랜잭션 불필요 (아직 차감 안됨).
     * </p>
     */
    DEBIT_FAILED("출금 실패", true, false),

    /**
     * 입금 실패
     * <p>
     * 입금 처리 중 실패한 상태.
     * 입금 계좌 동결, 한도 초과 등의 사유.
     * 보상 트랜잭션 필요 (이미 출금됨).
     * </p>
     */
    CREDIT_FAILED("입금 실패", false, true),

    // ========================================
    // 보상 트랜잭션 상태
    // ========================================

    /**
     * 보상 트랜잭션 진행 중
     * <p>
     * 입금 실패로 인해 출금 롤백을 진행 중인 상태.
     * 출금 계좌에 금액을 되돌리는 중.
     * </p>
     */
    COMPENSATING("보상 진행중", false, true),

    /**
     * 보상 완료
     * <p>
     * 출금 롤백이 완료되어 원상 복구된 상태.
     * 출금 계좌 잔액이 복구됨.
     * </p>
     */
    COMPENSATED("보상 완료", false, false),

    // ========================================
    // 최종 상태
    // ========================================

    /**
     * SAGA 완료 (성공)
     * <p>모든 단계가 성공적으로 완료된 최종 상태</p>
     */
    COMPLETED("완료", true, false),

    /**
     * SAGA 실패
     * <p>
     * SAGA가 실패한 최종 상태.
     * 출금 실패 또는 입금 실패 후 보상 완료.
     * </p>
     */
    FAILED("실패", true, false);

    private final String description;
    private final boolean isFinal;
    private final boolean requiresCompensation;  // 이 상태에서 실패 시 보상 필요

    SagaStatus(String description, boolean isFinal, boolean requiresCompensation) {
        this.description = description;
        this.isFinal = isFinal;
        this.requiresCompensation = requiresCompensation;
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
     * 보상 트랜잭션 필요 여부 확인
     * <p>
     * 이 상태에서 실패할 경우 보상 트랜잭션이 필요한지 여부.
     * 출금이 완료된 후의 상태들은 true.
     * </p>
     *
     * @return 보상 필요하면 true
     */
    public boolean requiresCompensation() {
        return requiresCompensation;
    }

    /**
     * 진행 중 여부 확인
     * <p>최종 상태가 아닌 모든 상태는 진행 중</p>
     *
     * @return 진행 중이면 true
     */
    public boolean isInProgress() {
        return !isFinal;
    }

    /**
     * 성공 완료 여부 확인
     *
     * @return COMPLETED이면 true
     */
    public boolean isCompleted() {
        return this == COMPLETED;
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
     * 보상 진행 중 여부 확인
     *
     * @return COMPENSATING이면 true
     */
    public boolean isCompensating() {
        return this == COMPENSATING;
    }

    /**
     * 특정 상태로 전환 가능 여부 확인
     *
     * @param target 전환하려는 상태
     * @return 전환 가능하면 true
     */
    public boolean canTransitionTo(SagaStatus target) {
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
    public Set<SagaStatus> getAllowedTransitions() {
        return switch (this) {
            case STARTED -> EnumSet.of(DEBIT_PENDING);
            case DEBIT_PENDING -> EnumSet.of(DEBIT_COMPLETED, DEBIT_FAILED);
            case DEBIT_COMPLETED -> EnumSet.of(CREDIT_PENDING);
            case DEBIT_FAILED -> EnumSet.of(FAILED);
            case CREDIT_PENDING -> EnumSet.of(CREDIT_COMPLETED, CREDIT_FAILED);
            case CREDIT_COMPLETED -> EnumSet.of(COMPLETED);
            case CREDIT_FAILED -> EnumSet.of(COMPENSATING);
            case COMPENSATING -> EnumSet.of(COMPENSATED);
            case COMPENSATED -> EnumSet.of(FAILED);
            case COMPLETED, FAILED -> EnumSet.noneOf(SagaStatus.class);
        };
    }

    /**
     * 다음 정상 상태 반환
     * <p>
     * 현재 상태에서 정상 진행 시 다음 상태를 반환합니다.
     * 최종 상태에서는 null을 반환합니다.
     * </p>
     *
     * @return 다음 정상 상태 또는 null
     */
    public SagaStatus nextSuccessStatus() {
        return switch (this) {
            case STARTED -> DEBIT_PENDING;
            case DEBIT_PENDING -> DEBIT_COMPLETED;
            case DEBIT_COMPLETED -> CREDIT_PENDING;
            case CREDIT_PENDING -> CREDIT_COMPLETED;
            case CREDIT_COMPLETED -> COMPLETED;
            default -> null;
        };
    }
}