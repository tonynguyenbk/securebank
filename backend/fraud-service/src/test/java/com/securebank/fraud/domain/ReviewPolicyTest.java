package com.securebank.fraud.domain;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.securebank.fraud.domain.FraudAlertStatus.APPROVED;
import static com.securebank.fraud.domain.FraudAlertStatus.CLOSED;
import static com.securebank.fraud.domain.FraudAlertStatus.OPEN;
import static com.securebank.fraud.domain.FraudAlertStatus.REJECTED;
import static com.securebank.fraud.domain.FraudAlertStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewPolicyTest {

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "OPEN,OPEN,false", "OPEN,UNDER_REVIEW,true", "OPEN,APPROVED,true", "OPEN,REJECTED,true", "OPEN,CLOSED,true",
            "UNDER_REVIEW,OPEN,false", "UNDER_REVIEW,UNDER_REVIEW,false", "UNDER_REVIEW,APPROVED,true",
            "UNDER_REVIEW,REJECTED,true", "UNDER_REVIEW,CLOSED,true",
            "APPROVED,OPEN,false", "APPROVED,UNDER_REVIEW,false", "APPROVED,REJECTED,false", "APPROVED,CLOSED,true",
            "REJECTED,UNDER_REVIEW,false", "REJECTED,APPROVED,false", "REJECTED,CLOSED,true",
            "CLOSED,OPEN,false", "CLOSED,UNDER_REVIEW,false", "CLOSED,APPROVED,false", "CLOSED,REJECTED,false",
            "CLOSED,CLOSED,false"})
    void transitionTable(FraudAlertStatus from, FraudAlertStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        if (!allowed) {
            assertThatThrownBy(() -> ReviewPolicy.validate(from, to, "a note"))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.FRAUD_ALERT_INVALID_TRANSITION));
        } else {
            assertThat(ReviewPolicy.validate(from, to, " a note ")).isEqualTo("a note");
        }
    }

    @Test
    void decisionsRequireANote() {
        for (FraudAlertStatus target : new FraudAlertStatus[]{APPROVED, REJECTED, CLOSED}) {
            assertThatThrownBy(() -> ReviewPolicy.validate(OPEN, target, "   "))
                    .isInstanceOfSatisfying(ApiException.class,
                            e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
            assertThatThrownBy(() -> ReviewPolicy.validate(OPEN, target, null))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void underReviewNoteIsOptional() {
        assertThat(ReviewPolicy.validate(OPEN, UNDER_REVIEW, null)).isNull();
    }

    @Test
    void invalidTransitionIsReportedBeforeMissingNote() {
        assertThatThrownBy(() -> ReviewPolicy.validate(CLOSED, APPROVED, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.FRAUD_ALERT_INVALID_TRANSITION));
    }

    @Test
    void noteLongerThan1000IsRejected() {
        assertThatThrownBy(() -> ReviewPolicy.validate(OPEN, REJECTED, "x".repeat(1001)))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
        assertThat(ReviewPolicy.validate(OPEN, REJECTED, "x".repeat(1000))).hasSize(1000);
    }
}
