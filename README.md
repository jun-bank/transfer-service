# Transfer Service

> 이체 서비스 - 계좌 간 이체, SAGA 오케스트레이터

## 📋 개요

| 항목 | 내용 |
|------|------|
| 포트 | 8083 |
| 데이터베이스 | transfer_db (PostgreSQL) |
| 주요 역할 | 이체 처리, 분산 트랜잭션 관리 (SAGA) |

## 🎯 학습 포인트

### 1. SAGA 패턴 ⭐ (핵심 학습 주제)

**SAGA 패턴이란?**
> 마이크로서비스 환경에서 여러 서비스에 걸친 트랜잭션을 관리하는 패턴

```
Transfer Service (오케스트레이터)         Account Service (참여자)
        │                                      │
   1. SAGA 시작                                │
        │──── 출금 요청 ─────────────────────>│
        │     (DEBIT_REQUESTED)               │
        │                                 2. 출금 처리
        │<─── 출금 완료 ──────────────────────│
        │                                      │
        │──── 입금 요청 ─────────────────────>│
        │     (CREDIT_REQUESTED)              │
        │                                 3. 입금 처리
        │<─── 입금 완료 ──────────────────────│
        │                                      │
   4. SAGA 완료 (SUCCESS)                      │
```

### 2. 보상 트랜잭션 (Compensating Transaction)

**입금 실패 시:**
```
출금 완료 상태에서 입금 실패
        │
        ▼
보상 트랜잭션 시작 (COMPENSATING)
        │──── 출금 롤백 요청 ───────────────>│
        │     (DEBIT_ROLLBACK)               │
        │                               출금 취소
        │<─── 롤백 완료 ─────────────────────│
        │
SAGA 실패 완료 (원상 복구됨)
```

### 3. Outbox 패턴

**문제:** DB 저장 성공 후 Kafka 발행 실패 → 데이터 불일치

**해결:**
```
┌──────────────────────────────────────┐
│        같은 DB 트랜잭션               │
│  ┌────────────┐  ┌────────────────┐  │
│  │  Transfer  │  │    Outbox      │  │
│  │   Table    │  │    Table       │  │
│  │            │  │ status: PENDING│  │
│  └────────────┘  └────────────────┘  │
└──────────────────────────────────────┘
              │
              │ 1. 동시 저장 (COMMIT)
              ▼
┌─────────────────────┐
│  Outbox Scheduler   │ ◄── 2. 주기적 폴링
└─────────────────────┘
              │
              │ 3. Kafka 발행
              ▼
┌─────────────────────┐
│       Kafka         │
└─────────────────────┘
              │
              ▼
  Outbox status: SENT ✓
```

---

## 🗄️ 도메인 모델

### 도메인 구조
```
domain/transfer/domain/
├── exception/
│   ├── TransferErrorCode.java      # 에러 코드 정의
│   └── TransferException.java      # 도메인 예외
└── model/
    ├── Transfer.java               # 이체 Aggregate Root
    ├── OutboxEvent.java            # Outbox 이벤트
    ├── TransferStatus.java         # 이체 상태 Enum
    ├── SagaStatus.java             # SAGA 상태 Enum
    ├── OutboxStatus.java           # Outbox 상태 Enum
    └── vo/
        ├── TransferId.java         # TRF-xxxxxxxx
        ├── OutboxEventId.java      # OBX-xxxxxxxx
        └── Money.java              # 금액 VO
```

### Transfer 도메인 모델
```
┌─────────────────────────────────────────────────────────────┐
│                         Transfer                             │
├─────────────────────────────────────────────────────────────┤
│ 【핵심 필드】                                                 │
│ transferId: TransferId (PK, TRF-xxxxxxxx)                   │
│ fromAccountNumber: String (출금 계좌)                       │
│ toAccountNumber: String (입금 계좌)                         │
│ amount: Money (이체 금액)                                   │
│ fee: Money (수수료)                                         │
│ status: TransferStatus (최종 결과)                          │
│ sagaStatus: SagaStatus (SAGA 진행 상태)                     │
│ failReason: String (실패 사유)                              │
│ memo: String (적요)                                         │
│ idempotencyKey: String (멱등성 키)                          │
│ requestedAt, completedAt                                    │
├─────────────────────────────────────────────────────────────┤
│ 【감사 필드 - BaseEntity】                                    │
│ createdAt, updatedAt, createdBy, updatedBy                  │
│ deletedAt, deletedBy, isDeleted                             │
├─────────────────────────────────────────────────────────────┤
│ 【SAGA 비즈니스 메서드】                                      │
│ + startSaga()              // STARTED → DEBIT_PENDING       │
│ + completeDebit()          // → DEBIT_COMPLETED → CREDIT_PENDING│
│ + failDebit(reason)        // → DEBIT_FAILED → FAILED       │
│ + completeCredit()         // → CREDIT_COMPLETED → COMPLETED│
│ + failCredit(reason)       // → CREDIT_FAILED → COMPENSATING│
│ + completeCompensation()   // → COMPENSATED → FAILED        │
│ + cancel(reason)           // → CANCELLED                   │
├─────────────────────────────────────────────────────────────┤
│ 【상태 확인 메서드】                                          │
│ + isNew(), isFinal(), isSuccess(), isFailed()               │
│ + isCancellable(), requiresCompensation()                   │
└─────────────────────────────────────────────────────────────┘
```

### SagaStatus Enum (SAGA 상태)
```
정상 흐름:
STARTED → DEBIT_PENDING → DEBIT_COMPLETED → CREDIT_PENDING → CREDIT_COMPLETED → COMPLETED

실패 흐름 (출금 실패):
STARTED → DEBIT_PENDING → DEBIT_FAILED → FAILED

실패 흐름 (입금 실패 → 보상):
... → CREDIT_PENDING → CREDIT_FAILED → COMPENSATING → COMPENSATED → FAILED
```

**정책 메서드:**
```java
public enum SagaStatus {
    STARTED, DEBIT_PENDING, DEBIT_COMPLETED, DEBIT_FAILED,
    CREDIT_PENDING, CREDIT_COMPLETED, CREDIT_FAILED,
    COMPENSATING, COMPENSATED, COMPLETED, FAILED;
    
    public boolean isFinal();
    public boolean requiresCompensation();
    public boolean canTransitionTo(SagaStatus target);
    public SagaStatus nextSuccessStatus();
}
```

### OutboxEvent 도메인 모델
```
┌─────────────────────────────────────────────────────────────┐
│                       OutboxEvent                            │
├─────────────────────────────────────────────────────────────┤
│ outboxEventId: OutboxEventId (PK, OBX-xxxxxxxx)             │
│ aggregateType: String ("Transfer")                          │
│ aggregateId: String (TRF-xxx)                               │
│ eventType: String ("DEBIT_REQUESTED")                       │
│ topic: String (Kafka 토픽)                                  │
│ payload: String (JSON)                                      │
│ status: OutboxStatus (PENDING/SENT/FAILED)                  │
│ retryCount: int                                             │
│ lastError: String                                           │
│ createdAt, sentAt                                           │
├─────────────────────────────────────────────────────────────┤
│ + markAsSent()                                              │
│ + incrementRetryCount(error)  // 최대 3회 초과 시 FAILED     │
│ + markAsFailed()                                            │
│ + requestRetry()              // FAILED → PENDING (수동)    │
└─────────────────────────────────────────────────────────────┘
```

### Exception 체계

#### TransferErrorCode
```java
public enum TransferErrorCode implements ErrorCode {
    // 유효성 (400)
    INVALID_TRANSFER_ID_FORMAT, INVALID_AMOUNT, SAME_ACCOUNT_TRANSFER,
    
    // 조회 (404)
    TRANSFER_NOT_FOUND, OUTBOX_EVENT_NOT_FOUND,
    
    // 계좌 (400)
    FROM_ACCOUNT_NOT_FOUND, TO_ACCOUNT_NOT_FOUND,
    INSUFFICIENT_BALANCE, NOT_ACCOUNT_OWNER,
    
    // 상태 (422)
    TRANSFER_ALREADY_COMPLETED, CANNOT_CANCEL_TRANSFER,
    INVALID_STATUS_TRANSITION, INVALID_SAGA_STATUS_TRANSITION,
    
    // SAGA (500)
    SAGA_DEBIT_FAILED, SAGA_CREDIT_FAILED,
    SAGA_COMPENSATION_FAILED, SAGA_TIMEOUT,
    
    // Outbox (500)
    OUTBOX_PUBLISH_FAILED, OUTBOX_MAX_RETRY_EXCEEDED;
}
```

---

## 📡 API 명세

### 1. 이체 요청
```http
POST /api/v1/transfers
X-User-Id: USR-a1b2c3d4
X-User-Role: USER
X-Idempotency-Key: transfer-uuid-12345

{
  "fromAccountNumber": "110-1234-5678-90",
  "toAccountNumber": "110-9876-5432-10",
  "amount": 50000,
  "memo": "월세 송금"
}
```

**SAGA 처리 흐름:**
1. Transfer.createBuilder().build() → status=PENDING, sagaStatus=STARTED
2. transfer.startSaga() → DEBIT_PENDING
3. OutboxEvent 생성 (DEBIT_REQUESTED)
4. Account Service 응답 대기...
5. completeDebit() / failDebit()
6. completeCredit() / failCredit()
7. 필요시 completeCompensation()

---

## 📂 패키지 구조

```
com.jun_bank.transfer_service
├── TransferServiceApplication.java
├── global/
│   ├── config/
│   ├── infrastructure/
│   │   ├── entity/
│   │   │   └── BaseEntity.java
│   │   └── jpa/
│   ├── security/
│   ├── feign/
│   └── aop/
└── domain/
    └── transfer/                        # Transfer Bounded Context
        ├── domain/                      # 순수 도메인 구현 완료
        │   ├── exception/
        │   │   ├── TransferErrorCode.java
        │   │   └── TransferException.java
        │   └── model/
        │       ├── Transfer.java            # Aggregate Root
        │       ├── OutboxEvent.java         # Outbox 이벤트
        │       ├── TransferStatus.java
        │       ├── SagaStatus.java
        │       ├── OutboxStatus.java
        │       └── vo/
        │           ├── TransferId.java
        │           ├── OutboxEventId.java
        │           └── Money.java
        ├── application/                 # 유스케이스 (TODO)
        │   ├── port/
        │   ├── service/
        │   ├── dto/
        │   └── saga/
        │       ├── SagaOrchestrator.java
        │       └── SagaStep.java
        ├── infrastructure/              # Adapter Out (TODO)
        │   ├── persistence/
        │   ├── kafka/
        │   └── outbox/
        │       ├── OutboxPublisher.java
        │       └── OutboxScheduler.java
        └── presentation/                # Adapter In (TODO)
            ├── controller/
            └── dto/
```

---

## 🔗 서비스 간 통신 (SAGA)

### Kafka 이벤트 (via Outbox)

**발행:**
| 이벤트 | 토픽 | 수신 |
|--------|------|------|
| DEBIT_REQUESTED | transfer.debit.requested | Account |
| CREDIT_REQUESTED | transfer.credit.requested | Account |
| DEBIT_ROLLBACK | transfer.debit.rollback | Account |
| TRANSFER_COMPLETED | transfer.completed | Ledger |
| TRANSFER_FAILED | transfer.failed | Ledger |

**수신:**
| 이벤트 | 토픽 | 발신 |
|--------|------|------|
| DEBIT_COMPLETED/FAILED | transfer.debit.* | Account |
| CREDIT_COMPLETED/FAILED | transfer.credit.* | Account |

---

## 📝 구현 체크리스트

### Domain Layer ✅
- [x] TransferErrorCode
- [x] TransferException
- [x] TransferStatus (정책 메서드)
- [x] SagaStatus (정책 메서드, 상태 전이)
- [x] OutboxStatus
- [x] TransferId VO
- [x] OutboxEventId VO
- [x] Money VO
- [x] Transfer (SAGA 오케스트레이션)
- [x] OutboxEvent (Outbox 패턴)

### Application Layer
- [ ] TransferUseCase
- [ ] SagaOrchestrator
- [ ] TransferPort
- [ ] OutboxPort
- [ ] DTO 정의

### Infrastructure Layer
- [ ] TransferEntity
- [ ] OutboxEventEntity
- [ ] JpaRepository
- [ ] OutboxScheduler
- [ ] TransferKafkaProducer
- [ ] TransferKafkaConsumer

### Presentation Layer
- [ ] TransferController
- [ ] Request/Response DTO
- [ ] Swagger 문서화

### 테스트
- [ ] 도메인 단위 테스트
- [ ] SAGA 정상 흐름 테스트
- [ ] 보상 트랜잭션 테스트
- [ ] Outbox 패턴 테스트