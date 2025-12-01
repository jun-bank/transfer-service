package com.jun_bank.transfer_service.domain.transfer.domain.model.vo;

import com.jun_bank.common_lib.util.UuidUtils;
import com.jun_bank.transfer_service.domain.transfer.domain.exception.TransferException;

/**
 * Outbox 이벤트 식별자 VO (Value Object)
 * <p>
 * Outbox 이벤트의 고유 식별자입니다.
 *
 * <h3>ID 형식:</h3>
 * <pre>OBX-xxxxxxxx (예: OBX-a1b2c3d4)</pre>
 *
 * @param value Outbox 이벤트 ID 문자열 (OBX-xxxxxxxx 형식)
 */
public record OutboxEventId(String value) {

    /**
     * ID 프리픽스
     */
    public static final String PREFIX = "OBX";

    /**
     * OutboxEventId 생성자 (Compact Constructor)
     *
     * @param value Outbox 이벤트 ID 문자열
     * @throws TransferException ID 형식이 유효하지 않은 경우
     */
    public OutboxEventId {
        if (!UuidUtils.isValidDomainId(value, PREFIX)) {
            throw TransferException.invalidOutboxEventIdFormat(value);
        }
    }

    /**
     * 문자열로부터 OutboxEventId 객체 생성
     *
     * @param value Outbox 이벤트 ID 문자열
     * @return OutboxEventId 객체
     */
    public static OutboxEventId of(String value) {
        return new OutboxEventId(value);
    }

    /**
     * 새로운 Outbox 이벤트 ID 생성
     *
     * @return 생성된 ID 문자열 (OBX-xxxxxxxx 형식)
     */
    public static String generateId() {
        return UuidUtils.generateDomainId(PREFIX);
    }
}