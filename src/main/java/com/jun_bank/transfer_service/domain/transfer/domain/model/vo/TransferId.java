package com.jun_bank.transfer_service.domain.transfer.domain.model.vo;

import com.jun_bank.common_lib.util.UuidUtils;
import com.jun_bank.transfer_service.domain.transfer.domain.exception.TransferException;

/**
 * 이체 식별자 VO (Value Object)
 * <p>
 * 이체의 고유 식별자입니다.
 *
 * <h3>ID 형식:</h3>
 * <pre>TRF-xxxxxxxx (예: TRF-a1b2c3d4)</pre>
 *
 * @param value 이체 ID 문자열 (TRF-xxxxxxxx 형식)
 */
public record TransferId(String value) {

    /**
     * ID 프리픽스
     */
    public static final String PREFIX = "TRF";

    /**
     * TransferId 생성자 (Compact Constructor)
     *
     * @param value 이체 ID 문자열
     * @throws TransferException ID 형식이 유효하지 않은 경우
     */
    public TransferId {
        if (!UuidUtils.isValidDomainId(value, PREFIX)) {
            throw TransferException.invalidTransferIdFormat(value);
        }
    }

    /**
     * 문자열로부터 TransferId 객체 생성
     *
     * @param value 이체 ID 문자열
     * @return TransferId 객체
     */
    public static TransferId of(String value) {
        return new TransferId(value);
    }

    /**
     * 새로운 이체 ID 생성
     *
     * @return 생성된 ID 문자열 (TRF-xxxxxxxx 형식)
     */
    public static String generateId() {
        return UuidUtils.generateDomainId(PREFIX);
    }
}