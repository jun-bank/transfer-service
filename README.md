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

**왜 필요한가?**
- MSA에서는 2PC(Two-Phase Commit) 사용 불가
- 각 서비스가 독립적인 DB를 가짐
- 분산 환경에서 데이터 일관성 유지 필요

```
┌─────────────────────────────────────────────────────────────┐
│                    이체 SAGA 흐름                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Transfer Service              Account Service             │
│   (오케스트레이터)               (참여자)                    │
│        │                             │                      │
│   1. 이체 요청 수신                  │                      │
│        │                             │                      │
│   2. SAGA 시작                       │                      │
│        │──── 출금 요청 ────────────>│                      │
│        │     (transfer.debit.requested)                     │
│        │                             │                      │
│        │                        3. 출금 처리                │
│        │                             │                      │
│        │<─── 출금 완료 ─────────────│                      │
│        │     (transfer.debit.completed)                     │
│        │                             │                      │
│   4. 입금 요청                       │                      │
│        │──── 입금 요청 ────────────>│                      │
│        │     (transfer.credit.requested)                    │
│        │                             │                      │
│        │                        5. 입금 처리                │
│        │                             │                      │
│        │<─── 입금 완료 ─────────────│                      │
│        │     (transfer.credit.completed)                    │
│        │                             │                      │
│   6. SAGA 완료                       │                      │
│        │                             │                      │
└─────────────────────────────────────────────────────────────┘
```

### 2. 보상 트랜잭션 (Compensating Transaction)

**입금 실패 시 보상 트랜잭션 흐름**

```
┌─────────────────────────────────────────────────────────────┐
│                    보상 트랜잭션 흐름                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   Transfer Service              Account Service             │
│        │                             │                      │
│   1. 출금 완료 상태                  │                      │
│        │                             │                      │
│   2. 입금 요청                       │                      │
│        │──── 입금 요청 ────────────>│                      │
│        │                             │                      │
│        │<─── 입금 실패! ────────────│                      │
│        │     (계좌 동결, 한도 초과 등)                       │
│        │                             │                      │
│   3. ⚠️ 보상 트랜잭션 시작           │                      │
│        │                             │                      │
│   4. 출금 롤백 요청                  │                      │
│        │──── 롤백 요청 ────────────>│                      │
│        │     (transfer.debit.rollback)                      │
│        │                             │                      │
│        │                        5. 출금 취소                │
│        │                           (금액 복구)              │
│        │                             │                      │
│        │<─── 롤백 완료 ─────────────│                      │
│        │                             │                      │
│   6. SAGA 실패 완료                  │                      │
│      (원상 복구됨)                   │                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 3. Outbox 패턴 ⭐

**Outbox 패턴이란?**
> DB 트랜잭션과 메시지 발행의 원자성을 보장하는 패턴

**문제 상황**
```
1. DB 저장 성공
2. Kafka 발행 실패 ← 데이터 불일치!
```

**해결: Outbox 패턴**
```
1. 비즈니스 데이터 + Outbox 테이블에 동시 저장 (같은 트랜잭션)
2. 별도 스케줄러가 Outbox 테이블 폴링
3. Outbox의 메시지를 Kafka로 발행
4. 발행 성공 시 Outbox 레코드 삭제/완료 처리
```

```
┌─────────────────────────────────────────────────────────────┐
│                    Outbox 패턴 흐름                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌──────────────────────────────────────┐                  │
│   │        같은 DB 트랜잭션               │                  │
│   │  ┌────────────┐  ┌────────────────┐  │                  │
│   │  │  Transfer  │  │    Outbox      │  │                  │
│   │  │   Table    │  │    Table       │  │                  │
│   │  │            │  │                │  │                  │
│   │  │ id: 1      │  │ id: 1          │  │                  │
│   │  │ amount:... │  │ event_type:... │  │                  │
│   │  │ status:... │  │ payload: JSON  │  │                  │
│   │  │            │  │ status: PENDING│  │                  │
│   │  └────────────┘  └────────────────┘  │                  │
│   └──────────────────────────────────────┘                  │
│              │                                              │
│              │ 1. 동시 저장 (COMMIT)                         │
│              ▼                                              │
│   ┌─────────────────────┐                                   │
│   │  Outbox Publisher   │ ◄── 2. 주기적 폴링 (1초)          │
│   │   (스케줄러)         │                                   │
│   └─────────────────────┘                                   │
│              │                                              │
│              │ 3. Kafka 발행                                │
│              ▼                                              │
│   ┌─────────────────────┐                                   │
│   │       Kafka         │                                   │
│   └─────────────────────┘                                   │
│              │                                              │
│              │ 4. 발행 성공 시                               │
│              ▼                                              │
│   ┌────────────────┐                                        │
│   │    Outbox      │                                        │
│   │ status: SENT ✓ │                                        │
│   └────────────────┘                                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🗄️ 도메인 모델

### Transfer Entity

```
┌─────────────────────────────────────────────┐
│                 Transfer                     │
├─────────────────────────────────────────────┤
│ id: Long (PK, Auto)                         │
│ transferId: String (UUID, Unique)           │
│ fromAccountNumber: String (출금 계좌)        │
│ toAccountNumber: String (입금 계좌)          │
│ amount: BigDecimal                          │
│ fee: BigDecimal (수수료)                     │
│ status: TransferStatus                      │
│ sagaStatus: SagaStatus                      │
│ failReason: String (실패 사유)               │
│ memo: String (적요)                         │
│ requestedAt: LocalDateTime                  │
│ completedAt: LocalDateTime                  │
└─────────────────────────────────────────────┘
```

### OutboxEvent Entity

```
┌─────────────────────────────────────────────┐
│               OutboxEvent                    │
├─────────────────────────────────────────────┤
│ id: Long (PK, Auto)                         │
│ aggregateType: String (ex: "Transfer")      │
│ aggregateId: String (ex: transferId)        │
│ eventType: String (ex: "DEBIT_REQUESTED")   │
│ payload: String (JSON)                      │
│ status: OutboxStatus (PENDING/SENT/FAILED)  │
│ retryCount: Integer                         │
│ createdAt: LocalDateTime                    │
│ sentAt: LocalDateTime                       │
└─────────────────────────────────────────────┘
```

### TransferStatus Enum
```java
public enum TransferStatus {
    PENDING,    // 처리 중
    SUCCESS,    // 성공
    FAILED,     // 실패
    CANCELLED   // 취소
}
```

### SagaStatus Enum
```java
public enum SagaStatus {
    STARTED,           // SAGA 시작
    DEBIT_PENDING,     // 출금 요청 중
    DEBIT_COMPLETED,   // 출금 완료
    DEBIT_FAILED,      // 출금 실패
    CREDIT_PENDING,    // 입금 요청 중
    CREDIT_COMPLETED,  // 입금 완료
    CREDIT_FAILED,     // 입금 실패
    COMPENSATING,      // 보상 트랜잭션 진행 중
    COMPENSATED,       // 보상 완료
    COMPLETED,         // SAGA 완료 (성공)
    FAILED             // SAGA 실패
}
```

---

## 📡 API 명세

### 1. 이체 요청
```http
POST /api/v1/transfers
X-User-Id: 1
X-User-Role: USER
X-Idempotency-Key: transfer-uuid-12345
Content-Type: application/json

{
  "fromAccountNumber": "110-1234-5678-90",
  "toAccountNumber": "110-9876-5432-10",
  "amount": 50000,
  "memo": "월세 송금"
}
```

**Response (202 Accepted)** - 비동기 처리
```json
{
  "transferId": "txf-uuid-abcd",
  "status": "PENDING",
  "fromAccountNumber": "110-1234-5678-90",
  "toAccountNumber": "110-9876-5432-10",
  "amount": 50000,
  "fee": 0,
  "memo": "월세 송금",
  "requestedAt": "2024-01-15T10:30:00",
  "message": "이체 요청이 접수되었습니다."
}
```

**이벤트 발행**: `transfer.debit.requested` (Outbox 통해)

---

### 2. 이체 상태 조회
```http
GET /api/v1/transfers/{transferId}
X-User-Id: 1
X-User-Role: USER
```

**Response (200 OK) - 성공**
```json
{
  "transferId": "txf-uuid-abcd",
  "status": "SUCCESS",
  "sagaStatus": "COMPLETED",
  "fromAccountNumber": "110-1234-5678-90",
  "toAccountNumber": "110-9876-5432-10",
  "amount": 50000,
  "fee": 0,
  "memo": "월세 송금",
  "requestedAt": "2024-01-15T10:30:00",
  "completedAt": "2024-01-15T10:30:02"
}
```

**Response (200 OK) - 실패**
```json
{
  "transferId": "txf-uuid-efgh",
  "status": "FAILED",
  "sagaStatus": "COMPENSATED",
  "fromAccountNumber": "110-1234-5678-90",
  "toAccountNumber": "110-9999-0000-00",
  "amount": 50000,
  "failReason": "수취 계좌가 동결 상태입니다.",
  "requestedAt": "2024-01-15T11:00:00",
  "completedAt": "2024-01-15T11:00:05"
}
```

---

### 3. 이체 내역 조회
```http
GET /api/v1/transfers?accountNumber=110-1234-5678-90&page=0&size=20
X-User-Id: 1
X-User-Role: USER
```

**Response (200 OK)**
```json
{
  "content": [
    {
      "transferId": "txf-uuid-abcd",
      "direction": "OUT",
      "counterpartyAccount": "110-9876-5432-10",
      "counterpartyName": "홍*동",
      "amount": 50000,
      "status": "SUCCESS",
      "memo": "월세 송금",
      "completedAt": "2024-01-15T10:30:02"
    },
    {
      "transferId": "txf-uuid-ijkl",
      "direction": "IN",
      "counterpartyAccount": "110-5555-6666-77",
      "counterpartyName": "김*수",
      "amount": 100000,
      "status": "SUCCESS",
      "memo": "생일 선물",
      "completedAt": "2024-01-14T15:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 30
}
```

---

### 4. 수취인 조회 (이체 전 확인)
```http
GET /api/v1/transfers/verify-recipient?accountNumber=110-9876-5432-10
X-User-Id: 1
X-User-Role: USER
```

**Response (200 OK)**
```json
{
  "accountNumber": "110-9876-5432-10",
  "ownerName": "홍*동",
  "bankName": "준뱅크",
  "valid": true
}
```

---

### 5. 이체 취소 (PENDING 상태만)
```http
POST /api/v1/transfers/{transferId}/cancel
X-User-Id: 1
X-User-Role: USER
```

**Response (200 OK)**
```json
{
  "transferId": "txf-uuid-abcd",
  "status": "CANCELLED",
  "message": "이체가 취소되었습니다."
}
```

---

## 📂 패키지 구조

```
com.jun_bank.transfer_service
├── TransferServiceApplication.java
├── global/                          # 전역 설정 레이어
│   ├── config/                      # 설정 클래스
│   │   ├── JpaConfig.java           # JPA Auditing 활성화
│   │   ├── QueryDslConfig.java      # QueryDSL JPAQueryFactory 빈
│   │   ├── KafkaProducerConfig.java # Kafka Producer (멱등성, JacksonJsonSerializer)
│   │   ├── KafkaConsumerConfig.java # Kafka Consumer (수동 ACK, JacksonJsonDeserializer)
│   │   ├── SecurityConfig.java      # Spring Security (헤더 기반 인증)
│   │   ├── FeignConfig.java         # Feign Client 설정
│   │   ├── SwaggerConfig.java       # OpenAPI 문서화
│   │   └── AsyncConfig.java         # 비동기 처리 (ThreadPoolTaskExecutor)
│   ├── infrastructure/
│   │   ├── entity/
│   │   │   └── BaseEntity.java      # 공통 엔티티 (Audit, Soft Delete)
│   │   └── jpa/
│   │       └── AuditorAwareImpl.java # JPA Auditing 사용자 정보
│   ├── security/
│   │   ├── UserPrincipal.java       # 인증 사용자 Principal
│   │   ├── HeaderAuthenticationFilter.java # Gateway 헤더 인증 필터
│   │   └── SecurityContextUtil.java # SecurityContext 유틸리티
│   ├── feign/
│   │   ├── FeignErrorDecoder.java   # Feign 에러 → BusinessException 변환
│   │   └── FeignRequestInterceptor.java # 인증 헤더 전파
│   └── aop/
│       └── LoggingAspect.java       # 요청/응답 로깅 AOP
└── domain/
    └── transfer/                    # Transfer 도메인
        ├── domain/                  # 순수 도메인 (Entity, VO, Enum)
        ├── application/             # 유스케이스, Port, DTO
        │   └── saga/                # SAGA 오케스트레이터 (추후 구현)
        │       ├── SagaOrchestrator.java
        │       └── SagaStep.java
        ├── infrastructure/          # Adapter (Out) - Repository, Kafka, Outbox
        │   └── outbox/              # Outbox 패턴 (추후 구현)
        │       ├── OutboxPublisher.java
        │       └── OutboxScheduler.java
        └── presentation/            # Adapter (In) - Controller
```

---

## 🔧 Global 레이어 상세

### Config 설정

| 클래스 | 설명 |
|--------|------|
| `JpaConfig` | JPA Auditing 활성화 (`@EnableJpaAuditing`) |
| `QueryDslConfig` | `JPAQueryFactory` 빈 등록 |
| `KafkaProducerConfig` | 멱등성 Producer (ENABLE_IDEMPOTENCE=true, ACKS=all) |
| `KafkaConsumerConfig` | 수동 ACK (MANUAL_IMMEDIATE), group-id: transfer-service-group |
| `SecurityConfig` | Stateless 세션, 헤더 기반 인증, CSRF 비활성화 |
| `FeignConfig` | 로깅 레벨 BASIC, 에러 디코더, 요청 인터셉터 |
| `SwaggerConfig` | OpenAPI 3.0 문서화 설정 |
| `AsyncConfig` | ThreadPoolTaskExecutor (core=5, max=10, queue=25) |

### Security 설정

| 클래스 | 설명 |
|--------|------|
| `HeaderAuthenticationFilter` | `X-User-Id`, `X-User-Role`, `X-User-Email` 헤더 → SecurityContext |
| `UserPrincipal` | `UserDetails` 구현체, 인증된 사용자 정보 |
| `SecurityContextUtil` | 현재 사용자 조회 유틸리티 |

### BaseEntity (Soft Delete 지원)

```java
@MappedSuperclass
public abstract class BaseEntity {
    private LocalDateTime createdAt;      // 생성일시 (자동)
    private LocalDateTime updatedAt;      // 수정일시 (자동)
    private String createdBy;             // 생성자 (자동)
    private String updatedBy;             // 수정자 (자동)
    private LocalDateTime deletedAt;      // 삭제일시
    private String deletedBy;             // 삭제자
    private Boolean isDeleted = false;    // 삭제 여부
    
    public void delete(String deletedBy);  // Soft Delete
    public void restore();                 // 복구
}
```

### 추후 구현 예정 (SAGA/Outbox)

| 클래스 | 설명 |
|--------|------|
| `SagaOrchestrator` | SAGA 패턴 오케스트레이터 |
| `OutboxPublisher` | Outbox 테이블 기반 이벤트 발행 |
| `OutboxScheduler` | Outbox 폴링 스케줄러 |

---

## 🔗 서비스 간 통신 (SAGA)

### 발행 이벤트 (Kafka Producer via Outbox)
| 이벤트 | 토픽 | 수신 서비스 | 설명 |
|--------|------|-------------|------|
| DEBIT_REQUESTED | transfer.debit.requested | Account | 출금 요청 |
| CREDIT_REQUESTED | transfer.credit.requested | Account | 입금 요청 |
| DEBIT_ROLLBACK | transfer.debit.rollback | Account | 출금 롤백 (보상) |
| TRANSFER_COMPLETED | transfer.completed | Ledger | 이체 완료 |
| TRANSFER_FAILED | transfer.failed | Ledger | 이체 실패 |

### 수신 이벤트 (Kafka Consumer)
| 이벤트 | 토픽 | 발신 서비스 | 설명 |
|--------|------|-------------|------|
| DEBIT_COMPLETED | transfer.debit.completed | Account | 출금 완료 응답 |
| DEBIT_FAILED | transfer.debit.failed | Account | 출금 실패 응답 |
| CREDIT_COMPLETED | transfer.credit.completed | Account | 입금 완료 응답 |
| CREDIT_FAILED | transfer.credit.failed | Account | 입금 실패 응답 |

### SAGA 상태 전이
```
STARTED 
  → DEBIT_PENDING 
    → DEBIT_COMPLETED → CREDIT_PENDING 
      → CREDIT_COMPLETED → COMPLETED ✅
      → CREDIT_FAILED → COMPENSATING → COMPENSATED → FAILED ❌
    → DEBIT_FAILED → FAILED ❌
```

---

## ⚙️ Outbox 설정

### application.yml
```yaml
transfer-service:
  outbox:
    polling-interval: 1000  # 1초
    batch-size: 100
    retention-days: 7
```

### OutboxScheduler
```java
@Scheduled(fixedDelayString = "${transfer-service.outbox.polling-interval}")
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxRepository
            .findByStatusOrderByCreatedAt(OutboxStatus.PENDING,
                    PageRequest.of(0, batchSize));

    for (OutboxEvent event : events) {
        try {
            kafkaTemplate.send(event.getTopic(), event.getPayload());
            event.markAsSent();
        } catch (Exception e) {
            event.incrementRetryCount();
            if (event.getRetryCount() >= maxRetries) {
                event.markAsFailed();
            }
        }
        outboxRepository.save(event);
    }
}
```

---

## 🧪 테스트 시나리오

### 1. 정상 이체 테스트
```bash
# 이체 요청
curl -X POST http://localhost:8080/api/v1/transfers \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -H "X-User-Role: USER" \
  -H "X-Idempotency-Key: test-transfer-1" \
  -d '{"fromAccountNumber":"110-1234-5678-90","toAccountNumber":"110-9876-5432-10","amount":50000}'

# 상태 확인 (폴링)
curl http://localhost:8080/api/v1/transfers/txf-uuid-abcd \
  -H "X-User-Id: 1" \
  -H "X-User-Role: USER"
```

### 2. 보상 트랜잭션 테스트
```java
@Test
void 입금_실패시_보상_트랜잭션_실행() {
    // Given: 수취 계좌가 동결 상태

    // When: 이체 요청

    // Then: 
    // 1. 출금 완료
    // 2. 입금 실패
    // 3. 보상 트랜잭션 발동
    // 4. 출금 롤백
    // 5. 원래 잔액 복구
}
```

### 3. Outbox 테스트
```java
@Test
void Outbox_패턴으로_메시지_발행_보장() {
    // Given: 이체 요청

    // When: 이체 처리 (Outbox에 저장)

    // Then:
    // 1. Transfer 테이블에 레코드 존재
    // 2. Outbox 테이블에 PENDING 이벤트 존재

    // When: OutboxScheduler 실행

    // Then:
    // 1. Kafka에 메시지 발행됨
    // 2. Outbox 이벤트 상태 = SENT
}
```

---

## 📝 구현 체크리스트

- [ ] Entity, Repository 생성
- [ ] TransferService 구현
- [ ] **TransferSagaOrchestrator 구현**
- [ ] **SagaStatus 상태 관리**
- [ ] **OutboxEvent 엔티티/리포지토리**
- [ ] **OutboxPublisher (스케줄러)**
- [ ] Controller 구현
- [ ] Kafka Producer 구현 (Outbox 통해)
- [ ] **Kafka Consumer 구현 (SAGA 응답 처리)**
- [ ] **보상 트랜잭션 구현**
- [ ] Feign Client 구현 (Account Service)
- [ ] SAGA 테스트 코드
- [ ] Outbox 테스트 코드
- [ ] 단위 테스트
- [ ] 통합 테스트
- [ ] API 문서화 (Swagger)