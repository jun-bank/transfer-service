package com.jun_bank.transfer_service.domain.transfer.domain.exception;

import com.jun_bank.common_lib.exception.ErrorCode;

/**
 * 이체 도메인 에러 코드
 * <p>
 * 이체 및 SAGA 관련 비즈니스 로직에서 발생할 수 있는 모든 에러를 정의합니다.
 *
 * <h3>에러 코드 체계:</h3>
 * <ul>
 *   <li>TRF_001~009: 유효성 검증 오류 (400)</li>
 *   <li>TRF_010~019: 조회 오류 (404)</li>
 *   <li>TRF_020~029: 계좌 관련 오류 (400)</li>
 *   <li>TRF_030~039: 상태 오류 (422)</li>
 *   <li>TRF_040~049: SAGA 오류 (500)</li>
 *   <li>TRF_050~059: Outbox 오류 (500)</li>
 * </ul>
 *
 * @see TransferException
 * @see ErrorCode
 */
public enum TransferErrorCode implements ErrorCode {

    // ========================================
    // 유효성 검증 오류 (400 Bad Request)
    // ========================================

    /**
     * 유효하지 않은 이체 ID 형식
     */
    INVALID_TRANSFER_ID_FORMAT("TRF_001", "유효하지 않은 이체 ID 형식입니다", 400),

    /**
     * 유효하지 않은 금액
     */
    INVALID_AMOUNT("TRF_002", "유효하지 않은 금액입니다", 400),

    /**
     * 최소 이체 금액 미달
     */
    MINIMUM_TRANSFER_AMOUNT("TRF_003", "최소 이체 금액은 1원입니다", 400),

    /**
     * 최대 이체 금액 초과
     */
    MAXIMUM_TRANSFER_AMOUNT("TRF_004", "1회 최대 이체 금액을 초과했습니다", 400),

    /**
     * 동일 계좌 이체 불가
     */
    SAME_ACCOUNT_TRANSFER("TRF_005", "동일한 계좌로는 이체할 수 없습니다", 400),

    /**
     * 멱등성 키 누락
     */
    IDEMPOTENCY_KEY_REQUIRED("TRF_006", "멱등성 키(X-Idempotency-Key)가 필요합니다", 400),

    /**
     * 유효하지 않은 Outbox 이벤트 ID 형식
     */
    INVALID_OUTBOX_EVENT_ID_FORMAT("TRF_007", "유효하지 않은 Outbox 이벤트 ID 형식입니다", 400),

    // ========================================
    // 조회 오류 (404 Not Found)
    // ========================================

    /**
     * 이체를 찾을 수 없음
     */
    TRANSFER_NOT_FOUND("TRF_010", "이체 내역을 찾을 수 없습니다", 404),

    /**
     * Outbox 이벤트를 찾을 수 없음
     */
    OUTBOX_EVENT_NOT_FOUND("TRF_011", "Outbox 이벤트를 찾을 수 없습니다", 404),

    // ========================================
    // 계좌 관련 오류 (400 Bad Request)
    // ========================================

    /**
     * 출금 계좌를 찾을 수 없음
     */
    FROM_ACCOUNT_NOT_FOUND("TRF_020", "출금 계좌를 찾을 수 없습니다", 400),

    /**
     * 입금 계좌를 찾을 수 없음
     */
    TO_ACCOUNT_NOT_FOUND("TRF_021", "입금 계좌를 찾을 수 없습니다", 400),

    /**
     * 출금 계좌 비활성
     */
    FROM_ACCOUNT_NOT_ACTIVE("TRF_022", "출금 계좌가 비활성 상태입니다", 400),

    /**
     * 입금 계좌 비활성
     */
    TO_ACCOUNT_NOT_ACTIVE("TRF_023", "입금 계좌가 비활성 상태입니다", 400),

    /**
     * 출금 계좌 잔액 부족
     */
    INSUFFICIENT_BALANCE("TRF_024", "출금 계좌의 잔액이 부족합니다", 400),

    /**
     * 본인 계좌가 아님
     */
    NOT_ACCOUNT_OWNER("TRF_025", "본인 소유 계좌가 아닙니다", 400),

    /**
     * 일일 이체 한도 초과
     */
    DAILY_TRANSFER_LIMIT_EXCEEDED("TRF_026", "일일 이체 한도를 초과했습니다", 400),

    // ========================================
    // 상태 오류 (422 Unprocessable Entity)
    // ========================================

    /**
     * 이미 완료된 이체
     */
    TRANSFER_ALREADY_COMPLETED("TRF_030", "이미 완료된 이체입니다", 422),

    /**
     * 이미 실패한 이체
     */
    TRANSFER_ALREADY_FAILED("TRF_031", "이미 실패한 이체입니다", 422),

    /**
     * 이미 취소된 이체
     */
    TRANSFER_ALREADY_CANCELLED("TRF_032", "이미 취소된 이체입니다", 422),

    /**
     * 취소 불가 상태
     */
    CANNOT_CANCEL_TRANSFER("TRF_033", "해당 상태에서는 이체를 취소할 수 없습니다", 422),

    /**
     * 허용되지 않은 상태 전이
     */
    INVALID_STATUS_TRANSITION("TRF_034", "허용되지 않은 상태 변경입니다", 422),

    /**
     * 허용되지 않은 SAGA 상태 전이
     */
    INVALID_SAGA_STATUS_TRANSITION("TRF_035", "허용되지 않은 SAGA 상태 변경입니다", 422),

    // ========================================
    // SAGA 오류 (500 Internal Server Error)
    // ========================================

    /**
     * SAGA 출금 실패
     */
    SAGA_DEBIT_FAILED("TRF_040", "출금 처리 중 오류가 발생했습니다", 500),

    /**
     * SAGA 입금 실패
     */
    SAGA_CREDIT_FAILED("TRF_041", "입금 처리 중 오류가 발생했습니다", 500),

    /**
     * SAGA 보상 트랜잭션 실패
     */
    SAGA_COMPENSATION_FAILED("TRF_042", "보상 트랜잭션 처리 중 오류가 발생했습니다", 500),

    /**
     * SAGA 타임아웃
     */
    SAGA_TIMEOUT("TRF_043", "이체 처리 시간이 초과되었습니다", 500),

    /**
     * 예상치 못한 SAGA 상태
     */
    UNEXPECTED_SAGA_STATE("TRF_044", "예상치 못한 SAGA 상태입니다", 500),

    // ========================================
    // Outbox 오류 (500 Internal Server Error)
    // ========================================

    /**
     * Outbox 이벤트 발행 실패
     */
    OUTBOX_PUBLISH_FAILED("TRF_050", "이벤트 발행에 실패했습니다", 500),

    /**
     * Outbox 최대 재시도 횟수 초과
     */
    OUTBOX_MAX_RETRY_EXCEEDED("TRF_051", "최대 재시도 횟수를 초과했습니다", 500);

    private final String code;
    private final String message;
    private final int status;

    TransferErrorCode(String code, String message, int status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public int getStatus() {
        return status;
    }
}