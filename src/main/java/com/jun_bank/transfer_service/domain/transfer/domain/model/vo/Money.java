package com.jun_bank.transfer_service.domain.transfer.domain.model.vo;

import com.jun_bank.transfer_service.domain.transfer.domain.exception.TransferException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * 금액 VO (Value Object) - Transfer Service
 * <p>
 * 이체 금액을 안전하게 다루기 위한 불변 객체입니다.
 *
 * <h3>특징:</h3>
 * <ul>
 *   <li>불변 객체</li>
 *   <li>0 이상만 허용</li>
 *   <li>소수점 없음 (원 단위)</li>
 * </ul>
 *
 * @param amount 금액 (BigDecimal, 0 이상)
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    private static final int SCALE = 0;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * 0원 상수
     */
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    /**
     * Money 생성자 (Compact Constructor)
     *
     * @param amount 금액
     * @throws TransferException 금액이 null이거나 음수인 경우
     */
    public Money {
        if (amount == null) {
            throw TransferException.invalidAmount(null);
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw TransferException.invalidAmount(amount);
        }
        amount = amount.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * long 값으로 Money 생성
     */
    public static Money of(long amount) {
        if (amount == 0) {
            return ZERO;
        }
        return new Money(BigDecimal.valueOf(amount));
    }

    /**
     * BigDecimal로 Money 생성
     */
    public static Money of(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return ZERO;
        }
        return new Money(amount);
    }

    /**
     * 문자열로 Money 생성
     */
    public static Money of(String amount) {
        return of(new BigDecimal(amount));
    }

    /**
     * 0원 여부 확인
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 양수 여부 확인
     */
    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 다른 금액보다 큰지 확인
     */
    public boolean isGreaterThan(Money other) {
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * 다른 금액보다 크거나 같은지 확인
     */
    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }

    /**
     * 금액 더하기
     */
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    /**
     * 금액 빼기
     */
    public Money subtract(Money other) {
        BigDecimal result = this.amount.subtract(other.amount);
        if (result.compareTo(BigDecimal.ZERO) < 0) {
            throw TransferException.insufficientBalance(this.amount, other.amount);
        }
        return new Money(result);
    }

    /**
     * 한국 원화 형식으로 포맷팅
     */
    public String formatted() {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.KOREA);
        return format.format(amount) + "원";
    }

    /**
     * long 값으로 변환
     */
    public long toLong() {
        return amount.longValue();
    }

    @Override
    public int compareTo(Money other) {
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}