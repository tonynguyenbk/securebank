package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.api.CreateTransferRequest;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferRequestValidatorTest {

    private final TransferRequestValidator validator = new TransferRequestValidator();

    @Test
    void normalizesAValidRequest() {
        TransferCommand command = validator.validate(new CreateTransferRequest(" 1000000001", "1000000002",
                new BigDecimal("1000000"), "vnd", "  Dinner  "));

        assertThat(command.sourceAccountNumber()).isEqualTo("1000000001");
        assertThat(command.amount()).isEqualByComparingTo("1000000").hasScaleOf(2);
        assertThat(command.currency()).isEqualTo("VND");
        assertThat(command.description()).isEqualTo("Dinner");
    }

    @Test
    void blankDescriptionBecomesNull() {
        assertThat(validator.validate(request("10", "VND", "   ")).description()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "10.555", "100000000000000000"})
    void rejectsInvalidAmounts(String amount) {
        assertCode(() -> validator.validate(request(amount, "VND", null)), ErrorCode.INVALID_TRANSFER_AMOUNT);
    }

    @Test
    void acceptsTrailingZerosBeyondTwoDecimals() {
        assertThat(validator.validate(request("10.500", "VND", null)).amount()).isEqualByComparingTo("10.50");
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertCode(() -> validator.validate(request("10", "USD", null)), ErrorCode.CURRENCY_NOT_SUPPORTED);
    }

    @Test
    void rejectsSameAccount() {
        assertCode(() -> validator.validate(new CreateTransferRequest("1000000001", "1000000001", BigDecimal.TEN,
                "VND", null)), ErrorCode.SAME_ACCOUNT_TRANSFER);
    }

    @Test
    void validatesIdempotencyKey() {
        assertCode(() -> validator.validateIdempotencyKey(" "), ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        assertCode(() -> validator.validateIdempotencyKey(null), ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        assertCode(() -> validator.validateIdempotencyKey("short"), ErrorCode.VALIDATION_FAILED);
        assertCode(() -> validator.validateIdempotencyKey("has spaces in it"), ErrorCode.VALIDATION_FAILED);
        assertThat(validator.validateIdempotencyKey("5f0c7f8e-2b5d-4a1e-9c3f-7d1b2a6e4c10"))
                .isEqualTo("5f0c7f8e-2b5d-4a1e-9c3f-7d1b2a6e4c10");
    }

    private static CreateTransferRequest request(String amount, String currency, String description) {
        return new CreateTransferRequest("1000000001", "1000000002", new BigDecimal(amount), currency, description);
    }

    private static void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(code));
    }
}
