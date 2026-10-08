package com.securebank.bankingcore.integration;

import com.jayway.jsonpath.JsonPath;
import com.securebank.bankingcore.support.IntegrationTestSupport;
import com.securebank.bankingcore.support.TestBank.TestAccount;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;

import java.util.UUID;

import static com.securebank.bankingcore.support.TransferRequests.newKey;
import static com.securebank.bankingcore.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reconciliation, limits management, admin searches, stats and ledger immutability. */
class AdminOperationsIntegrationTest extends IntegrationTestSupport {

    @Test
    void reconciliationOfASuccessfulTransferIsBalanced() throws Exception {
        TestAccount a = bank.open("Rec Alice", "10000000.00");
        TestAccount b = bank.open("Rec Bob", "0.00");
        String tx = JsonPath.read(mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(),
                        b.accountNumber(), "2500000.50", null)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.transactionId");

        mvc.perform(get("/api/v1/admin/reconciliation/transactions/" + tx).header("Authorization", auditorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.entryCount").value(2))
                .andExpect(jsonPath("$.debitTotal").value(2500000.50))
                .andExpect(jsonPath("$.creditTotal").value(2500000.50))
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.entries[0].entryType").value("DEBIT"))
                .andExpect(jsonPath("$.entries[0].accountNumber").value(a.accountNumber()));
    }

    @Test
    void reconciliationOfARejectedTransferHasNoEntriesAndIsBalanced() throws Exception {
        TestAccount a = bank.open("Rej Alice", "1.00");
        TestAccount b = bank.open("Rej Bob", "0.00");
        mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "5", null))
                .andExpect(status().isUnprocessableEntity());
        UUID tx = jdbc.queryForObject("SELECT id FROM bank_transactions WHERE source_account_id = ?", UUID.class,
                a.accountId());

        mvc.perform(get("/api/v1/admin/reconciliation/transactions/" + tx).header("Authorization", adminToken()))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.entryCount").value(0))
                .andExpect(jsonPath("$.balanced").value(true));
        mvc.perform(get("/api/v1/admin/transactions/" + tx).header("Authorization", staffToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureCode").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.destinationCustomerName").value("Rej Bob"))
                .andExpect(jsonPath("$.ledgerEntries.length()").value(0));
    }

    @Test
    void staffUpdatesLimitsWithAuditAndTheNewLimitIsEnforced() throws Exception {
        TestAccount a = bank.open("Limited Alice", "10000000.00");
        TestAccount b = bank.open("Bob", "0.00");

        mvc.perform(put("/api/v1/admin/accounts/" + a.accountId() + "/limits").header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perTransactionLimit\":1000000,\"dailyLimit\":1500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(a.accountId().toString()))
                .andExpect(jsonPath("$.perTransactionLimit").value(1000000.00))
                .andExpect(jsonPath("$.dailyLimit").value(1500000.00));
        assertThat(jdbc.queryForObject("""
                SELECT (payload->'before'->>'perTransactionLimit') || '>' || (payload->'after'->>'perTransactionLimit')
                FROM outbox_events WHERE aggregate_id = ? AND payload->>'action' = 'TRANSFER_LIMIT_UPDATE'
                """, String.class, a.accountId())).isEqualTo("100000000.00>1000000.00");

        String token = customerToken(a);
        mvc.perform(transfer(token, newKey(), a.accountNumber(), b.accountNumber(), "1000001", null))
                .andExpect(jsonPath("$.code").value("TRANSFER_LIMIT_EXCEEDED"));
        mvc.perform(transfer(token, newKey(), a.accountNumber(), b.accountNumber(), "1000000", null))
                .andExpect(status().isCreated());
        mvc.perform(transfer(token, newKey(), a.accountNumber(), b.accountNumber(), "600000", null))
                .andExpect(jsonPath("$.code").value("DAILY_LIMIT_EXCEEDED"));
        mvc.perform(get("/api/v1/accounts/" + a.accountId()).header("Authorization", token))
                .andExpect(jsonPath("$.limits.usedToday").value(1000000.00))
                .andExpect(jsonPath("$.limits.remainingToday").value(500000.00));
    }

    @Test
    void invalidLimitsAreRejected() throws Exception {
        TestAccount a = bank.open("Alice", "0.00");
        mvc.perform(put("/api/v1/admin/accounts/" + a.accountId() + "/limits").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perTransactionLimit\":2000,\"dailyLimit\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/v1/admin/accounts/" + UUID.randomUUID() + "/limits").header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"perTransactionLimit\":1000,\"dailyLimit\":1000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminSearchesAndStats() throws Exception {
        String unique = "Zebra" + UUID.randomUUID().toString().substring(0, 8);
        TestAccount a = bank.open(unique + " Nguyễn", "1000.00");
        TestAccount b = bank.open("Other", "0.00");
        String ref = JsonPath.read(mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(),
                b.accountNumber(), "100", null)).andReturn().getResponse().getContentAsString(),
                "$.transactionReference");
        String staff = staffToken();

        mvc.perform(get("/api/v1/admin/customers").param("q", unique.toLowerCase()).header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value(unique + " Nguyễn"))
                .andExpect(jsonPath("$.content[0].accountCount").value(1));
        mvc.perform(get("/api/v1/admin/customers/" + a.customerId()).header("Authorization", staff))
                .andExpect(jsonPath("$.accounts[0].accountNumber").value(a.accountNumber()));
        mvc.perform(get("/api/v1/admin/accounts").param("q", a.accountNumber()).header("Authorization", staff))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].customerName").value(unique + " Nguyễn"));
        mvc.perform(get("/api/v1/admin/transactions").param("reference", ref).header("Authorization", staff))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sourceAccountNumber").value(a.accountNumber()));
        mvc.perform(get("/api/v1/admin/transactions").param("accountNumber", b.accountNumber())
                        .param("status", "SUCCESS").header("Authorization", staff))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/admin/stats/today").header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("VND"));
        mvc.perform(get("/api/v1/admin/stats/daily").param("days", "7").header("Authorization", staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));
        mvc.perform(get("/api/v1/admin/stats/daily").param("days", "91").header("Authorization", staff))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ledgerIsAppendOnlyAtDatabaseLevel() throws Exception {
        TestAccount a = bank.open("Immutable", "1000.00");
        TestAccount b = bank.open("Immutable B", "0.00");
        mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(), b.accountNumber(), "10", null))
                .andExpect(status().isCreated());

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = 1 WHERE account_id = ?",
                a.accountId())).isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE account_id = ?", a.accountId()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE accounts SET balance = -1 WHERE id = ?", a.accountId()))
                .isInstanceOf(DataAccessException.class);
    }
}
