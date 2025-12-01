package com.jun_bank.transfer_service.domain.transfer.domain.model;

import com.jun_bank.transfer_service.domain.transfer.domain.exception.TransferException;
import com.jun_bank.transfer_service.domain.transfer.domain.model.vo.OutboxEventId;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Outbox 이벤트 도메인 모델
 * <p>
 * Outbox 패턴을 구현하여 DB 트랜잭션과 메시지 발행의 원자성을 보장합니다.
 *
 * <h3>Outbox 패턴 흐름:</h3>
 * <ol>
 *   <li>비즈니스 로직과 함께 OutboxEvent를 같은 트랜잭션으로 저장</li>
 *   <li>OutboxScheduler가 PENDING 상태의 이벤트를 폴링</li>
 *   <li>Kafka로 발행 성공 시 SENT로 상태 변경</li>
 *   <li>실패 시 retryCount 증가, 최대 횟수 초과 시 FAILED</li>
 * </ol>
 *
 * <h3>이벤트 타입 (Transfer SAGA):</h3>
 * <ul>
 *   <li>DEBIT_REQUESTED: 출금 요청</li>
 *   <li>CREDIT_REQUESTED: 입금 요청</li>
 *   <li>DEBIT_ROLLBACK: 출금 롤백 (보상)</li>
 *   <li>TRANSFER_COMPLETED: 이체 완료</li>
 *   <li>TRANSFER_FAILED: 이체 실패</li>
 * </ul>
 *
 * @see OutboxStatus
 */
@Getter
public class OutboxEvent {

    /**
     * 기본 최대 재시도 횟수
     */
    public static final int DEFAULT_MAX_RETRY = 3;

    // ========================================
    // 핵심 필드
    // ========================================

    /**
     * Outbox 이벤트 ID
     */
    private OutboxEventId outboxEventId;

    /**
     * Aggregate 타입 (예: "Transfer")
     */
    private String aggregateType;

    /**
     * Aggregate ID (예: TRF-xxx)
     */
    private String aggregateId;

    /**
     * 이벤트 타입 (예: "DEBIT_REQUESTED")
     */
    private String eventType;

    /**
     * Kafka 토픽
     */
    private String topic;

    /**
     * 이벤트 페이로드 (JSON)
     */
    private String payload;

    /**
     * 발행 상태
     */
    private OutboxStatus status;

    /**
     * 재시도 횟수
     */
    private int retryCount;

    /**
     * 마지막 오류 메시지
     */
    private String lastError;

    /**
     * 생성 시간
     */
    private LocalDateTime createdAt;

    /**
     * 발행 완료 시간
     */
    private LocalDateTime sentAt;

    private OutboxEvent() {}

    // ========================================
    // 팩토리 메서드
    // ========================================

    /**
     * 새 Outbox 이벤트 생성
     *
     * @param aggregateType Aggregate 타입
     * @param aggregateId Aggregate ID
     * @param eventType 이벤트 타입
     * @param topic Kafka 토픽
     * @param payload JSON 페이로드
     * @return 새 OutboxEvent
     */
    public static OutboxEvent create(
            String aggregateType,
            String aggregateId,
            String eventType,
            String topic,
            String payload) {

        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.topic = topic;
        event.payload = payload;
        event.status = OutboxStatus.PENDING;
        event.retryCount = 0;
        event.createdAt = LocalDateTime.now();

        return event;
    }

    /**
     * DB 복원용 빌더
     *
     * @return OutboxEventRestoreBuilder
     */
    public static OutboxEventRestoreBuilder restoreBuilder() {
        return new OutboxEventRestoreBuilder();
    }

    // ========================================
    // 상태 확인 메서드
    // ========================================

    /**
     * 신규 여부 확인
     */
    public boolean isNew() {
        return this.outboxEventId == null;
    }

    /**
     * 발행 대기 여부 확인
     */
    public boolean isPending() {
        return this.status.isPending();
    }

    /**
     * 발행 완료 여부 확인
     */
    public boolean isSent() {
        return this.status.isSent();
    }

    /**
     * 실패 여부 확인
     */
    public boolean isFailed() {
        return this.status.isFailed();
    }

    /**
     * 재시도 가능 여부 확인
     */
    public boolean canRetry() {
        return this.status.isRetryable() && this.retryCount < DEFAULT_MAX_RETRY;
    }

    // ========================================
    // 비즈니스 메서드
    // ========================================

    /**
     * 발행 완료 처리
     */
    public void markAsSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * 재시도 횟수 증가
     * <p>
     * 최대 재시도 횟수 초과 시 FAILED로 변경됩니다.
     * </p>
     *
     * @param errorMessage 오류 메시지
     */
    public void incrementRetryCount(String errorMessage) {
        this.retryCount++;
        this.lastError = errorMessage;

        if (this.retryCount >= DEFAULT_MAX_RETRY) {
            markAsFailed();
        }
    }

    /**
     * 실패 처리
     */
    public void markAsFailed() {
        this.status = OutboxStatus.FAILED;
    }

    /**
     * 재발행 요청 (수동 재시도)
     * <p>
     * FAILED 상태의 이벤트를 PENDING으로 되돌립니다.
     * retryCount는 유지됩니다.
     * </p>
     */
    public void requestRetry() {
        if (!this.status.isFailed()) {
            throw TransferException.outboxPublishFailed(
                    this.outboxEventId != null ? this.outboxEventId.value() : "NEW",
                    "FAILED 상태의 이벤트만 재시도 가능합니다");
        }
        this.status = OutboxStatus.PENDING;
    }

    // ========================================
    // Builder 클래스
    // ========================================

    public static class OutboxEventRestoreBuilder {
        private OutboxEventId outboxEventId;
        private String aggregateType;
        private String aggregateId;
        private String eventType;
        private String topic;
        private String payload;
        private OutboxStatus status;
        private int retryCount;
        private String lastError;
        private LocalDateTime createdAt;
        private LocalDateTime sentAt;

        public OutboxEventRestoreBuilder outboxEventId(OutboxEventId outboxEventId) {
            this.outboxEventId = outboxEventId;
            return this;
        }

        public OutboxEventRestoreBuilder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public OutboxEventRestoreBuilder aggregateId(String aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public OutboxEventRestoreBuilder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public OutboxEventRestoreBuilder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public OutboxEventRestoreBuilder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public OutboxEventRestoreBuilder status(OutboxStatus status) {
            this.status = status;
            return this;
        }

        public OutboxEventRestoreBuilder retryCount(int retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public OutboxEventRestoreBuilder lastError(String lastError) {
            this.lastError = lastError;
            return this;
        }

        public OutboxEventRestoreBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public OutboxEventRestoreBuilder sentAt(LocalDateTime sentAt) {
            this.sentAt = sentAt;
            return this;
        }

        public OutboxEvent build() {
            OutboxEvent event = new OutboxEvent();
            event.outboxEventId = this.outboxEventId;
            event.aggregateType = this.aggregateType;
            event.aggregateId = this.aggregateId;
            event.eventType = this.eventType;
            event.topic = this.topic;
            event.payload = this.payload;
            event.status = this.status;
            event.retryCount = this.retryCount;
            event.lastError = this.lastError;
            event.createdAt = this.createdAt;
            event.sentAt = this.sentAt;
            return event;
        }
    }
}