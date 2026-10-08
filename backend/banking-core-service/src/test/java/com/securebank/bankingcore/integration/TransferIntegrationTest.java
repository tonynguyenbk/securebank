package com.securebank.bankingcore.integration;

import com.jayway.jsonpath.JsonPath;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.support.IntegrationTestSupport;
import com.securebank.bankingcore.support.TestBank.TestAccount;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static com.securebank.bankingcore.support.TransferRequests.newKey;
import static com.securebank.bankingcore.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec §36 transfer scenarios end-to-end over HTTP against real PostgreSQL. */
class TransferIntegrationTest extends IntegrationTestSupport {

    @Test
    void successfulTransferMovesMoneyWritesTwoLedgerEntriesAndOneOutboxEvent() throws Exception {
        TestAccount a = bank.open("Alice Success", "10000000.00");
        TestAccount b = bank.open("Bob Success", "5000000.00");

        MvcResult result = mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(),
                        "1000000", "Dinner payment"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.transactionReference", startsWith("TX")))
                .andExpect(jsonPath("$.amount").value(1000000.00))
                .andExpect(jsonPath("$.remainingBalance").value(9000000.00))
                .andExpect(jsonPath("$.destinationHolderName").value("Bob Success"))
                .andExpect(jsonPath("$.ledgerEntries.length()").value(2))
                .andExpect(jsonPath("$.ledgerEntries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.ledgerEntries[0].accountNumber").value(a.accountNumber()))
                .andExpect(jsonPath("$.ledgerEntries[0].balanceBefore").value(10000000.00))
                .andExpect(jsonPath("$.ledgerEntries[0].balanceAfter").value(9000000.00))
                // privacy rule: the recipient's line is masked and has no balances
                .andExpect(jsonPath("$.ledgerEntries[1].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.ledgerEntries[1].accountNumber", startsWith("******")))
                .andExpect(jsonPath("$.ledgerEntries[1].balanceBefore").doesNotExist())
                .andReturn();

        UUID txId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId"));
        assertThat(bank.balance(a)).isEqualByComparingTo("9000000");
        assertThat(bank.balance(b)).isEqualByComparingTo("6000000");
        assertThat(jdbc.queryForObject("SELECT status FROM bank_transactions WHERE id = ?", String.class, txId))
                .isEqualTo("SUCCESS");
        assertThat(ledgerCount(txId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'TRANSACTION_COMPLETED'
                """, Integer.class, txId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events
                WHERE aggregate_id = ? AND event_type = 'AUDIT' AND payload->>'action' = 'TRANSFER_COMPLETED'
                """, Integer.class, txId)).isEqualTo(1);
    }

    @Test
    void insufficientFundsIsRejectedAndRecordedWithoutLedger() throws Exception {
        TestAccount a = bank.open("Poor Alice", "500000.00");
        TestAccount b = bank.open("Bob", "0.00");

        MvcResult result = mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(),
                        "1000000", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andReturn();

        assertThat(bank.balance(a)).isEqualByComparingTo("500000");
        assertThat(bank.balance(b)).isEqualByComparingTo("0");
        UUID rejectedId = onlyTransactionOf(a, "REJECTED");
        assertThat(ledgerCount(rejectedId)).isZero();
        assertThat(jdbc.queryForObject("SELECT failure_code FROM bank_transactions WHERE id = ?", String.class,
                rejectedId)).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'TRANSACTION_FAILED'
                """, Integer.class, rejectedId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND payload->>'action' = 'TRANSFER_REJECTED'
                """, Integer.class, rejectedId)).isEqualTo(1);
        assertThat(result.getResponse().getContentAsString()).contains("\"status\":422");
    }

    @Test
    void frozenSourceCannotSend() throws Exception {
        TestAccount a = bank.open("Frozen Alice", "5000000.00");
        TestAccount b = bank.open("Bob", "0.00");
        bank.setStatus(a, AccountStatus.FROZEN);

        mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "100000", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_FROZEN"));

        assertThat(bank.balance(a)).isEqualByComparingTo("5000000");
        assertThat(bank.balance(b)).isEqualByComparingTo("0");
        onlyTransactionOf(a, "REJECTED");
    }

    @Test
    void frozenDestinationStillReceives() throws Exception {
        TestAccount a = bank.open("Alice", "5000000.00");
        TestAccount b = bank.open("Frozen Bob", "0.00");
        bank.setStatus(b, AccountStatus.FROZEN);

        mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "100000", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        assertThat(bank.balance(b)).isEqualByComparingTo("100000");
    }

    @Test
    void closedDestinationIsRejected() throws Exception {
        TestAccount a = bank.open("Alice", "5000000.00");
        TestAccount b = bank.open("Closed Bob", "0.00");
        bank.setStatus(b, AccountStatus.CLOSED);

        mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "100000", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_CLOSED"));
        assertThat(bank.balance(a)).isEqualByComparingTo("5000000");
    }

    @Test
    void perTransactionLimitExceededIsRejected() throws Exception {
        TestAccount a = bank.open("Rich Alice", "300000000.00");
        TestAccount b = bank.open("Bob", "0.00");

        mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "100000000.01", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TRANSFER_LIMIT_EXCEEDED"));

        assertThat(bank.balance(a)).isEqualByComparingTo("300000000");
        onlyTransactionOf(a, "REJECTED");
    }

    @Test
    void dailyLimitExceededIsRejected() throws Exception {
        TestAccount a = bank.open("Very Rich Alice", "900000000.00");
        TestAccount b = bank.open("Bob", "0.00");
        String token = customerToken(a);
        for (int i = 0; i < 5; i++) {   // 5 x 100M = 500M = the whole daily limit
            mvc.perform(transfer(token, newKey(), a.accountNumber(), b.accountNumber(), "100000000", null))
                    .andExpect(status().isCreated());
        }

        mvc.perform(transfer(token, newKey(), a.accountNumber(), b.accountNumber(), "1", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));

        assertThat(bank.balance(a)).isEqualByComparingTo("400000000");
        assertThat(bank.balance(b)).isEqualByComparingTo("500000000");
    }

    @Test
    void duplicateIdempotencyKeyReplaysStoredResponseWithoutMovingMoneyAgain() throws Exception {
        TestAccount a = bank.open("Retry Alice", "10000000.00");
        TestAccount b = bank.open("Bob", "0.00");
        String key = newKey();
        String token = customerToken(a);

        String first = mvc.perform(transfer(token, key, a.accountNumber(), b.accountNumber(), "1000000", "retry"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        // same payload, amount written differently (1000000.00 vs 1000000) -> same canonical request
        String second = mvc.perform(transfer(token, key, a.accountNumber(), b.accountNumber(), "1000000.00", "retry"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(bank.balance(a)).isEqualByComparingTo("9000000");
        assertThat(bank.balance(b)).isEqualByComparingTo("1000000");
        assertThat(transactionCount(a)).isEqualTo(1);
    }

    @Test
    void rejectedTransferIsReplayedWithTheSameRejection() throws Exception {
        TestAccount a = bank.open("Alice", "10.00");
        TestAccount b = bank.open("Bob", "0.00");
        String key = newKey();
        String token = customerToken(a);

        String first = mvc.perform(transfer(token, key, a.accountNumber(), b.accountNumber(), "100", null))
                .andExpect(status().isUnprocessableEntity()).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(transfer(token, key, a.accountNumber(), b.accountNumber(), "100", null))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(transactionCount(a)).isEqualTo(1);
    }

    @Test
    void sameKeyWithDifferentPayloadIsConflict() throws Exception {
        TestAccount a = bank.open("Alice", "10000000.00");
        TestAccount b = bank.open("Bob", "0.00");
        String key = newKey();
        String token = customerToken(a);

        mvc.perform(transfer(token, key, a.accountNumber(), b.accountNumber(), "1000", null))
                .andExpect(status().isCreated());
        mvc.perform(transfer(token, key, a.accountNumber(), b.accountNumber(), "2000", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));

        assertThat(bank.balance(a)).isEqualByComparingTo("9999000");
    }

    @Test
    void failureAfterDebitRollsBackEverything() throws Exception {
        TestAccount a = bank.open("Rollback Alice", "10000000.00");
        TestAccount b = bank.open("Rollback Bob", "5000000.00");
        faultInjector.arm();

        mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "1000000", null))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(bank.balance(a)).isEqualByComparingTo("10000000");
        assertThat(bank.balance(b)).isEqualByComparingTo("5000000");
        assertThat(transactionCount(a)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id IN (?, ?)",
                Integer.class, a.accountId(), b.accountId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE user_id = ?",
                Integer.class, a.userId())).isZero();
    }

    @Test
    void validationErrorsAreNotPersisted() throws Exception {
        TestAccount a = bank.open("Alice", "10000000.00");
        TestAccount b = bank.open("Bob", "0.00");
        String token = customerToken(a);

        mvc.perform(transfer(token, null, a.accountNumber(), b.accountNumber(), "1000", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(transfer(token, newKey(), a.accountNumber(), b.accountNumber(), "10.001", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSFER_AMOUNT"));
        mvc.perform(transfer(token, newKey(), a.accountNumber(), b.accountNumber(), "-5", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSFER_AMOUNT"));
        mvc.perform(transfer(token, newKey(), a.accountNumber(), a.accountNumber(), "1000", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));
        mvc.perform(transfer(token, newKey(), a.accountNumber(), "9999999999", "1000", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        // someone else's account as source
        mvc.perform(transfer(token, newKey(), b.accountNumber(), a.accountNumber(), "1000", null))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_OWNED"));

        assertThat(transactionCount(a)).isZero();
        assertThat(transactionCount(b)).isZero();
        assertThat(bank.balance(b)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void recipientSeesIncomingTransferButNotTheSendersRejections() throws Exception {
        TestAccount a = bank.open("Sender", "1000000.00");
        TestAccount b = bank.open("Recipient", "0.00");
        String ok = mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "1000", "hi"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "99999999", null))
                .andExpect(status().isUnprocessableEntity());
        String txId = JsonPath.read(ok, "$.transactionId");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/transfers")
                        .header("Authorization", customerToken(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("IN"))
                .andExpect(jsonPath("$.content[0].counterpartyName").value("Sender"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/transfers")
                        .header("Authorization", customerToken(a)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/transfers/" + txId)
                        .header("Authorization", customerToken(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("IN"))
                .andExpect(jsonPath("$.ledgerEntries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.ledgerEntries[0].balanceBefore").doesNotExist())
                .andExpect(jsonPath("$.ledgerEntries[1].accountNumber").value(b.accountNumber()))
                .andExpect(jsonPath("$.ledgerEntries[1].balanceAfter").value(1000.00));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/accounts/" + b.accountId() + "/statement")
                        .header("Authorization", customerToken(b)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].entryType").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].counterpartyAccountNumber").value(a.accountNumber()))
                .andExpect(jsonPath("$.content[0].counterpartyName").value("Sender"));
    }

    private int ledgerCount(UUID txId) {
        return jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE transaction_id = ?", Integer.class, txId);
    }

    private int transactionCount(TestAccount source) {
        return jdbc.queryForObject("SELECT count(*) FROM bank_transactions WHERE source_account_id = ?",
                Integer.class, source.accountId());
    }

    private UUID onlyTransactionOf(TestAccount source, String expectedStatus) {
        var rows = jdbc.queryForList("SELECT id, status FROM bank_transactions WHERE source_account_id = ?",
                source.accountId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("status")).isEqualTo(expectedStatus);
        return (UUID) rows.get(0).get("id");
    }
}
