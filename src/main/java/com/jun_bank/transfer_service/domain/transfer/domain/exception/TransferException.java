package com.jun_bank.transfer_service.domain.transfer.domain.exception;

import com.jun_bank.common_lib.exception.BusinessException;

import java.math.BigDecimal;

/**
 * 이체 도메인 예외
 * <p>
 * 이체 및 SAGA 관련 비즈니스 로직에서 발생하는 예외를 처리합니다.
 *
 * <h3>사용 예:</h3>
 * <pre>{@code
 * throw TransferException.transferNotFound("TRF-a1b2c3d4");
 * throw TransferException.insufficientBalance(currentBalance, requestedAmount);
 * throw TransferException.sagaDebitFailed("TRF-xxx", "계좌 동결");
 * }</pre>
 *
 * @see TransferErrorCode
 * @see BusinessException
 */
public class TransferException extends BusinessException {

    public TransferException(TransferErrorCode errorCode) {
        super(errorCode);
    }

    public TransferException(TransferErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    // ========================================
    // 유효성 검증 관련 팩토리 메서드
    // ========================================

    /**
     * 유효하지 않은 이체 ID 형식 예외 생성
     */
    public static TransferException invalidTransferIdFormat(String id) {
        return new TransferException(TransferErrorCode.INVALID_TRANSFER_ID_FORMAT, "id=" + id);
    }

    /**
     * 유효하지 않은 금액 예외 생성
     */
    public static TransferException invalidAmount(BigDecimal amount) {
        return new TransferException(TransferErrorCode.INVALID_AMOUNT,
                "amount=" + (amount != null ? amount.toPlainString() : "null"));
    }

    /**
     * 최소 이체 금액 미달 예외 생성
     */
    public static TransferException minimumTransferAmount(BigDecimal amount, BigDecimal minimum) {
        return new TransferException(TransferErrorCode.MINIMUM_TRANSFER_AMOUNT,
                String.format("요청=%s, 최소=%s", amount.toPlainString(), minimum.toPlainString()));
    }

    /**
     * 최대 이체 금액 초과 예외 생성
     */
    public static TransferException maximumTransferAmount(BigDecimal amount, BigDecimal maximum) {
        return new TransferException(TransferErrorCode.MAXIMUM_TRANSFER_AMOUNT,
                String.format("요청=%s, 최대=%s", amount.toPlainString(), maximum.toPlainString()));
    }

    /**
     * 동일 계좌 이체 불가 예외 생성
     */
    public static TransferException sameAccountTransfer(String accountNumber) {
        return new TransferException(TransferErrorCode.SAME_ACCOUNT_TRANSFER,
                "accountNumber=" + accountNumber);
    }

    /**
     * 멱등성 키 누락 예외 생성
     */
    public static TransferException idempotencyKeyRequired() {
        return new TransferException(TransferErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    /**
     * 유효하지 않은 Outbox 이벤트 ID 형식 예외 생성
     */
    public static TransferException invalidOutboxEventIdFormat(String id) {
        return new TransferException(TransferErrorCode.INVALID_OUTBOX_EVENT_ID_FORMAT, "id=" + id);
    }

    // ========================================
    // 조회 관련 팩토리 메서드
    // ========================================

    /**
     * 이체를 찾을 수 없음 예외 생성
     */
    public static TransferException transferNotFound(String transferId) {
        return new TransferException(TransferErrorCode.TRANSFER_NOT_FOUND,
                "transferId=" + transferId);
    }

    /**
     * Outbox 이벤트를 찾을 수 없음 예외 생성
     */
    public static TransferException outboxEventNotFound(String eventId) {
        return new TransferException(TransferErrorCode.OUTBOX_EVENT_NOT_FOUND,
                "eventId=" + eventId);
    }

    // ========================================
    // 계좌 관련 팩토리 메서드
    // ========================================

    /**
     * 출금 계좌를 찾을 수 없음 예외 생성
     */
    public static TransferException fromAccountNotFound(String accountNumber) {
        return new TransferException(TransferErrorCode.FROM_ACCOUNT_NOT_FOUND,
                "accountNumber=" + accountNumber);
    }

    /**
     * 입금 계좌를 찾을 수 없음 예외 생성
     */
    public static TransferException toAccountNotFound(String accountNumber) {
        return new TransferException(TransferErrorCode.TO_ACCOUNT_NOT_FOUND,
                "accountNumber=" + accountNumber);
    }

    /**
     * 출금 계좌 비활성 예외 생성
     */
    public static TransferException fromAccountNotActive(String accountNumber, String status) {
        return new TransferException(TransferErrorCode.FROM_ACCOUNT_NOT_ACTIVE,
                String.format("accountNumber=%s, status=%s", accountNumber, status));
    }

    /**
     * 입금 계좌 비활성 예외 생성
     */
    public static TransferException toAccountNotActive(String accountNumber, String status) {
        return new TransferException(TransferErrorCode.TO_ACCOUNT_NOT_ACTIVE,
                String.format("accountNumber=%s, status=%s", accountNumber, status));
    }

    /**
     * 잔액 부족 예외 생성
     */
    public static TransferException insufficientBalance(BigDecimal currentBalance, BigDecimal requestedAmount) {
        return new TransferException(TransferErrorCode.INSUFFICIENT_BALANCE,
                String.format("현재잔액=%s, 요청금액=%s",
                        currentBalance.toPlainString(), requestedAmount.toPlainString()));
    }

    /**
     * 본인 계좌가 아님 예외 생성
     */
    public static TransferException notAccountOwner(String accountNumber) {
        return new TransferException(TransferErrorCode.NOT_ACCOUNT_OWNER,
                "accountNumber=" + accountNumber);
    }

    /**
     * 일일 이체 한도 초과 예외 생성
     */
    public static TransferException dailyTransferLimitExceeded(BigDecimal usedAmount, BigDecimal limitAmount) {
        return new TransferException(TransferErrorCode.DAILY_TRANSFER_LIMIT_EXCEEDED,
                String.format("사용=%s, 한도=%s", usedAmount.toPlainString(), limitAmount.toPlainString()));
    }

    // ========================================
    // 상태 관련 팩토리 메서드
    // ========================================

    /**
     * 이미 완료된 이체 예외 생성
     */
    public static TransferException transferAlreadyCompleted(String transferId) {
        return new TransferException(TransferErrorCode.TRANSFER_ALREADY_COMPLETED,
                "transferId=" + transferId);
    }

    /**
     * 이미 실패한 이체 예외 생성
     */
    public static TransferException transferAlreadyFailed(String transferId) {
        return new TransferException(TransferErrorCode.TRANSFER_ALREADY_FAILED,
                "transferId=" + transferId);
    }

    /**
     * 이미 취소된 이체 예외 생성
     */
    public static TransferException transferAlreadyCancelled(String transferId) {
        return new TransferException(TransferErrorCode.TRANSFER_ALREADY_CANCELLED,
                "transferId=" + transferId);
    }

    /**
     * 취소 불가 상태 예외 생성
     */
    public static TransferException cannotCancelTransfer(String transferId, String status) {
        return new TransferException(TransferErrorCode.CANNOT_CANCEL_TRANSFER,
                String.format("transferId=%s, status=%s", transferId, status));
    }

    /**
     * 허용되지 않은 상태 전이 예외 생성
     */
    public static TransferException invalidStatusTransition(String from, String to) {
        return new TransferException(TransferErrorCode.INVALID_STATUS_TRANSITION,
                String.format("from=%s, to=%s", from, to));
    }

    /**
     * 허용되지 않은 SAGA 상태 전이 예외 생성
     */
    public static TransferException invalidSagaStatusTransition(String from, String to) {
        return new TransferException(TransferErrorCode.INVALID_SAGA_STATUS_TRANSITION,
                String.format("from=%s, to=%s", from, to));
    }

    // ========================================
    // SAGA 관련 팩토리 메서드
    // ========================================

    /**
     * SAGA 출금 실패 예외 생성
     */
    public static TransferException sagaDebitFailed(String transferId, String reason) {
        return new TransferException(TransferErrorCode.SAGA_DEBIT_FAILED,
                String.format("transferId=%s, reason=%s", transferId, reason));
    }

    /**
     * SAGA 입금 실패 예외 생성
     */
    public static TransferException sagaCreditFailed(String transferId, String reason) {
        return new TransferException(TransferErrorCode.SAGA_CREDIT_FAILED,
                String.format("transferId=%s, reason=%s", transferId, reason));
    }

    /**
     * SAGA 보상 트랜잭션 실패 예외 생성
     */
    public static TransferException sagaCompensationFailed(String transferId, String reason) {
        return new TransferException(TransferErrorCode.SAGA_COMPENSATION_FAILED,
                String.format("transferId=%s, reason=%s", transferId, reason));
    }

    /**
     * SAGA 타임아웃 예외 생성
     */
    public static TransferException sagaTimeout(String transferId) {
        return new TransferException(TransferErrorCode.SAGA_TIMEOUT,
                "transferId=" + transferId);
    }

    /**
     * 예상치 못한 SAGA 상태 예외 생성
     */
    public static TransferException unexpectedSagaState(String transferId, String status) {
        return new TransferException(TransferErrorCode.UNEXPECTED_SAGA_STATE,
                String.format("transferId=%s, status=%s", transferId, status));
    }

    // ========================================
    // Outbox 관련 팩토리 메서드
    // ========================================

    /**
     * Outbox 이벤트 발행 실패 예외 생성
     */
    public static TransferException outboxPublishFailed(String eventId, String reason) {
        return new TransferException(TransferErrorCode.OUTBOX_PUBLISH_FAILED,
                String.format("eventId=%s, reason=%s", eventId, reason));
    }

    /**
     * Outbox 최대 재시도 횟수 초과 예외 생성
     */
    public static TransferException outboxMaxRetryExceeded(String eventId, int retryCount) {
        return new TransferException(TransferErrorCode.OUTBOX_MAX_RETRY_EXCEEDED,
                String.format("eventId=%s, retryCount=%d", eventId, retryCount));
    }
}