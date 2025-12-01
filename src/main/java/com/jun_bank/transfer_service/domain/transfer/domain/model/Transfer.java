package com.jun_bank.transfer_service.domain.transfer.domain.model;

import com.jun_bank.transfer_service.domain.transfer.domain.exception.TransferException;
import com.jun_bank.transfer_service.domain.transfer.domain.model.vo.Money;
import com.jun_bank.transfer_service.domain.transfer.domain.model.vo.TransferId;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 이체 도메인 모델 (Aggregate Root)
 * <p>
 * 이체의 핵심 비즈니스 로직과 SAGA 상태를 관리합니다.
 *
 * <h3>책임:</h3>
 * <ul>
 *   <li>이체 요청 생성 및 검증</li>
 *   <li>SAGA 상태 전이 관리</li>
 *   <li>이체 결과 상태 관리</li>
 *   <li>보상 트랜잭션 트리거</li>
 * </ul>
 *
 * <h3>SAGA 흐름:</h3>
 * <pre>
 * 1. startSaga()      → STARTED → DEBIT_PENDING
 * 2. completeDebit()  → DEBIT_COMPLETED → CREDIT_PENDING
 * 3. completeCredit() → CREDIT_COMPLETED → COMPLETED + SUCCESS
 *    또는
 * 3. failCredit()     → CREDIT_FAILED → COMPENSATING
 * 4. completeCompensation() → COMPENSATED → FAILED
 * </pre>
 *
 * @see TransferStatus
 * @see SagaStatus
 */
@Getter
public class Transfer {

    // ========================================
    // 핵심 필드
    // ========================================

    /**
     * 이체 ID
     */
    private TransferId transferId;

    /**
     * 출금 계좌번호
     */
    private String fromAccountNumber;

    /**
     * 입금 계좌번호
     */
    private String toAccountNumber;

    /**
     * 이체 금액
     */
    private Money amount;

    /**
     * 수수료
     */
    private Money fee;

    /**
     * 이체 상태 (최종 결과)
     */
    private TransferStatus status;

    /**
     * SAGA 상태 (진행 상태)
     */
    private SagaStatus sagaStatus;

    /**
     * 실패 사유
     */
    private String failReason;

    /**
     * 메모 (적요)
     */
    private String memo;

    /**
     * 멱등성 키
     */
    private String idempotencyKey;

    /**
     * 요청 시간
     */
    private LocalDateTime requestedAt;

    /**
     * 완료 시간
     */
    private LocalDateTime completedAt;

    // ========================================
    // 감사 필드 (BaseEntity 매핑)
    // ========================================

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime deletedAt;
    private String deletedBy;
    private Boolean isDeleted;

    private Transfer() {}

    // ========================================
    // 생성 메서드
    // ========================================

    /**
     * 신규 이체 생성 빌더
     *
     * @return TransferCreateBuilder
     */
    public static TransferCreateBuilder createBuilder() {
        return new TransferCreateBuilder();
    }

    /**
     * DB 복원용 빌더
     *
     * @return TransferRestoreBuilder
     */
    public static TransferRestoreBuilder restoreBuilder() {
        return new TransferRestoreBuilder();
    }

    // ========================================
    // 상태 확인 메서드
    // ========================================

    /**
     * 신규 여부 확인
     */
    public boolean isNew() {
        return this.transferId == null;
    }

    /**
     * 최종 상태 여부 확인
     */
    public boolean isFinal() {
        return this.status.isFinal();
    }

    /**
     * 성공 여부 확인
     */
    public boolean isSuccess() {
        return this.status.isSuccess();
    }

    /**
     * 실패 여부 확인
     */
    public boolean isFailed() {
        return this.status.isFailed();
    }

    /**
     * 취소 가능 여부 확인
     */
    public boolean isCancellable() {
        return this.status.isCancellable() &&
                (this.sagaStatus == SagaStatus.STARTED || this.sagaStatus == SagaStatus.DEBIT_PENDING);
    }

    /**
     * 보상 트랜잭션 필요 여부 확인
     */
    public boolean requiresCompensation() {
        return this.sagaStatus.requiresCompensation();
    }

    // ========================================
    // SAGA 비즈니스 메서드
    // ========================================

    /**
     * SAGA 시작 - 출금 요청
     * <p>
     * STARTED → DEBIT_PENDING으로 전이합니다.
     * </p>
     */
    public void startSaga() {
        validateSagaTransition(SagaStatus.DEBIT_PENDING);
        this.sagaStatus = SagaStatus.DEBIT_PENDING;
    }

    /**
     * 출금 완료 - 입금 요청으로 진행
     * <p>
     * DEBIT_PENDING → DEBIT_COMPLETED → CREDIT_PENDING으로 전이합니다.
     * </p>
     */
    public void completeDebit() {
        validateSagaTransition(SagaStatus.DEBIT_COMPLETED);
        this.sagaStatus = SagaStatus.DEBIT_COMPLETED;

        // 바로 입금 요청 상태로 전이
        this.sagaStatus = SagaStatus.CREDIT_PENDING;
    }

    /**
     * 출금 실패
     * <p>
     * DEBIT_PENDING → DEBIT_FAILED → FAILED로 전이합니다.
     * 보상 트랜잭션 불필요.
     * </p>
     *
     * @param reason 실패 사유
     */
    public void failDebit(String reason) {
        validateSagaTransition(SagaStatus.DEBIT_FAILED);
        this.sagaStatus = SagaStatus.DEBIT_FAILED;
        this.failReason = reason;

        // 바로 최종 실패 상태로
        this.sagaStatus = SagaStatus.FAILED;
        this.status = TransferStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 입금 완료 - SAGA 성공
     * <p>
     * CREDIT_PENDING → CREDIT_COMPLETED → COMPLETED로 전이합니다.
     * </p>
     */
    public void completeCredit() {
        validateSagaTransition(SagaStatus.CREDIT_COMPLETED);
        this.sagaStatus = SagaStatus.CREDIT_COMPLETED;

        // SAGA 완료
        this.sagaStatus = SagaStatus.COMPLETED;
        this.status = TransferStatus.SUCCESS;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 입금 실패 - 보상 트랜잭션 시작
     * <p>
     * CREDIT_PENDING → CREDIT_FAILED → COMPENSATING으로 전이합니다.
     * </p>
     *
     * @param reason 실패 사유
     */
    public void failCredit(String reason) {
        validateSagaTransition(SagaStatus.CREDIT_FAILED);
        this.sagaStatus = SagaStatus.CREDIT_FAILED;
        this.failReason = reason;

        // 보상 트랜잭션 시작
        this.sagaStatus = SagaStatus.COMPENSATING;
    }

    /**
     * 보상 트랜잭션 완료
     * <p>
     * COMPENSATING → COMPENSATED → FAILED로 전이합니다.
     * </p>
     */
    public void completeCompensation() {
        validateSagaTransition(SagaStatus.COMPENSATED);
        this.sagaStatus = SagaStatus.COMPENSATED;

        // SAGA 실패 완료
        this.sagaStatus = SagaStatus.FAILED;
        this.status = TransferStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 보상 트랜잭션 실패
     * <p>
     * 심각한 상황. 수동 개입 필요.
     * </p>
     *
     * @param reason 실패 사유
     */
    public void failCompensation(String reason) {
        // 보상 실패는 매우 심각한 상황
        this.failReason = "보상 실패: " + reason;
        this.sagaStatus = SagaStatus.FAILED;
        this.status = TransferStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 이체 취소
     * <p>
     * PENDING 상태에서만 취소 가능합니다.
     * </p>
     *
     * @param reason 취소 사유
     */
    public void cancel(String reason) {
        if (!isCancellable()) {
            throw TransferException.cannotCancelTransfer(
                    this.transferId != null ? this.transferId.value() : "NEW",
                    this.status.name());
        }

        this.status = TransferStatus.CANCELLED;
        this.sagaStatus = SagaStatus.FAILED;
        this.failReason = "취소: " + reason;
        this.completedAt = LocalDateTime.now();
    }

    // ========================================
    // Private 검증 메서드
    // ========================================

    private void validateSagaTransition(SagaStatus target) {
        // SAGA가 완료된 후에는 추가 전이 불가
        if (this.sagaStatus.isFinal()) {
            throw TransferException.invalidSagaStatusTransition(
                    this.sagaStatus.name(), target.name());
        }
    }

    // ========================================
    // Builder 클래스
    // ========================================

    public static class TransferCreateBuilder {
        private String fromAccountNumber;
        private String toAccountNumber;
        private Money amount;
        private Money fee = Money.ZERO;
        private String memo;
        private String idempotencyKey;

        public TransferCreateBuilder fromAccountNumber(String fromAccountNumber) {
            this.fromAccountNumber = fromAccountNumber;
            return this;
        }

        public TransferCreateBuilder toAccountNumber(String toAccountNumber) {
            this.toAccountNumber = toAccountNumber;
            return this;
        }

        public TransferCreateBuilder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public TransferCreateBuilder fee(Money fee) {
            this.fee = fee;
            return this;
        }

        public TransferCreateBuilder memo(String memo) {
            this.memo = memo;
            return this;
        }

        public TransferCreateBuilder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public Transfer build() {
            // 동일 계좌 검증
            if (fromAccountNumber != null && fromAccountNumber.equals(toAccountNumber)) {
                throw TransferException.sameAccountTransfer(fromAccountNumber);
            }

            // 금액 검증
            if (amount == null || !amount.isPositive()) {
                throw TransferException.invalidAmount(
                        amount != null ? amount.amount() : null);
            }

            Transfer transfer = new Transfer();
            transfer.fromAccountNumber = this.fromAccountNumber;
            transfer.toAccountNumber = this.toAccountNumber;
            transfer.amount = this.amount;
            transfer.fee = this.fee;
            transfer.memo = this.memo;
            transfer.idempotencyKey = this.idempotencyKey;
            transfer.status = TransferStatus.PENDING;
            transfer.sagaStatus = SagaStatus.STARTED;
            transfer.requestedAt = LocalDateTime.now();
            transfer.isDeleted = false;

            return transfer;
        }
    }

    public static class TransferRestoreBuilder {
        private TransferId transferId;
        private String fromAccountNumber;
        private String toAccountNumber;
        private Money amount;
        private Money fee;
        private TransferStatus status;
        private SagaStatus sagaStatus;
        private String failReason;
        private String memo;
        private String idempotencyKey;
        private LocalDateTime requestedAt;
        private LocalDateTime completedAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private String createdBy;
        private String updatedBy;
        private LocalDateTime deletedAt;
        private String deletedBy;
        private Boolean isDeleted;

        public TransferRestoreBuilder transferId(TransferId transferId) {
            this.transferId = transferId;
            return this;
        }

        public TransferRestoreBuilder fromAccountNumber(String fromAccountNumber) {
            this.fromAccountNumber = fromAccountNumber;
            return this;
        }

        public TransferRestoreBuilder toAccountNumber(String toAccountNumber) {
            this.toAccountNumber = toAccountNumber;
            return this;
        }

        public TransferRestoreBuilder amount(Money amount) {
            this.amount = amount;
            return this;
        }

        public TransferRestoreBuilder fee(Money fee) {
            this.fee = fee;
            return this;
        }

        public TransferRestoreBuilder status(TransferStatus status) {
            this.status = status;
            return this;
        }

        public TransferRestoreBuilder sagaStatus(SagaStatus sagaStatus) {
            this.sagaStatus = sagaStatus;
            return this;
        }

        public TransferRestoreBuilder failReason(String failReason) {
            this.failReason = failReason;
            return this;
        }

        public TransferRestoreBuilder memo(String memo) {
            this.memo = memo;
            return this;
        }

        public TransferRestoreBuilder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public TransferRestoreBuilder requestedAt(LocalDateTime requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }

        public TransferRestoreBuilder completedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
            return this;
        }

        public TransferRestoreBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public TransferRestoreBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public TransferRestoreBuilder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public TransferRestoreBuilder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        public TransferRestoreBuilder deletedAt(LocalDateTime deletedAt) {
            this.deletedAt = deletedAt;
            return this;
        }

        public TransferRestoreBuilder deletedBy(String deletedBy) {
            this.deletedBy = deletedBy;
            return this;
        }

        public TransferRestoreBuilder isDeleted(Boolean isDeleted) {
            this.isDeleted = isDeleted;
            return this;
        }

        public Transfer build() {
            Transfer transfer = new Transfer();
            transfer.transferId = this.transferId;
            transfer.fromAccountNumber = this.fromAccountNumber;
            transfer.toAccountNumber = this.toAccountNumber;
            transfer.amount = this.amount;
            transfer.fee = this.fee;
            transfer.status = this.status;
            transfer.sagaStatus = this.sagaStatus;
            transfer.failReason = this.failReason;
            transfer.memo = this.memo;
            transfer.idempotencyKey = this.idempotencyKey;
            transfer.requestedAt = this.requestedAt;
            transfer.completedAt = this.completedAt;
            transfer.createdAt = this.createdAt;
            transfer.updatedAt = this.updatedAt;
            transfer.createdBy = this.createdBy;
            transfer.updatedBy = this.updatedBy;
            transfer.deletedAt = this.deletedAt;
            transfer.deletedBy = this.deletedBy;
            transfer.isDeleted = this.isDeleted;
            return transfer;
        }
    }
}