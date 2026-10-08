package com.securebank.bankingcore.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money rules shared by the domain: BigDecimal only, two decimals, VND only in v1 (spec §6). */
public final class Money {

    public static final String VND = "VND";
    public static final int SCALE = 2;
    /** NUMERIC(19,2) leaves 17 integer digits. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999999.99");

    private Money() {
    }

    /** Normalizes a value that is already known to have at most two significant decimals. */
    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** True when the amount is positive, has at most 2 significant decimals and fits NUMERIC(19,2). */
    public static boolean isValidTransferAmount(BigDecimal amount) {
        return amount != null
                && amount.signum() > 0
                && amount.stripTrailingZeros().scale() <= SCALE
                && amount.compareTo(MAX_AMOUNT) <= 0;
    }
}
