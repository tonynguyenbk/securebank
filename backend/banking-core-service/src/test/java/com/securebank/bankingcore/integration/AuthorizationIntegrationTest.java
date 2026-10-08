package com.securebank.bankingcore.integration;

import com.jayway.jsonpath.JsonPath;
import com.securebank.bankingcore.domain.AccountStatus;
import com.securebank.bankingcore.support.IntegrationTestSupport;
import com.securebank.bankingcore.support.TestBank.TestAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static com.securebank.bankingcore.support.TransferRequests.newKey;
import static com.securebank.bankingcore.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spec §36 "Authorization" and "Staff permissions", plus the contract's role columns. */
class AuthorizationIntegrationTest extends IntegrationTestSupport {

    private static final String REASON = "{\"reason\":\"Suspected fraud\"}";
    private static final String LIMITS = "{\"perTransactionLimit\":1000000,\"dailyLimit\":5000000}";

    @Test
    void unauthenticatedRequestsGet401() throws Exception {
        mvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotReadAnotherCustomersAccountOrTransaction() throws Exception {
        TestAccount a = bank.open("Customer A", "1000000.00");
        TestAccount b = bank.open("Customer B", "1000000.00");
        TestAccount c = bank.open("Customer C", "0.00");
        String bTx = JsonPath.read(mvc.perform(transfer(customerToken(b), newKey(), b.accountNumber(),
                c.accountNumber(), "1000", null)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.transactionId");
        String tokenA = customerToken(a);

        mvc.perform(get("/api/v1/accounts/" + b.accountId()).header("Authorization", tokenA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_OWNED"));
        mvc.perform(get("/api/v1/accounts/" + b.accountId() + "/balance").header("Authorization", tokenA))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts/" + b.accountId() + "/statement").header("Authorization", tokenA))
                .andExpect(status().isForbidden());
        // not a party -> 404, existence is not leaked
        mvc.perform(get("/api/v1/transfers/" + bTx).header("Authorization", tokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
        mvc.perform(get("/api/v1/transfers").param("accountId", b.accountId().toString())
                        .header("Authorization", tokenA))
                .andExpect(status().isForbidden());

        // own data works, and only own accounts are listed
        mvc.perform(get("/api/v1/accounts/" + a.accountId()).header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limits.perTransactionLimit").value(100000000.00))
                .andExpect(jsonPath("$.limits.remainingToday").value(500000000.00));
        mvc.perform(get("/api/v1/accounts").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].accountNumber").value(a.accountNumber()));
        mvc.perform(get("/api/v1/customers/me").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Customer A"));
        mvc.perform(get("/api/v1/accounts/lookup").param("accountNumber", b.accountNumber())
                        .header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Customer B"));
    }

    @Test
    void customerCannotCallAdminEndpoints() throws Exception {
        TestAccount a = bank.open("Nosy Customer", "0.00");
        mvc.perform(get("/api/v1/admin/accounts").header("Authorization", customerToken(a)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
        mvc.perform(patch("/api/v1/admin/accounts/" + a.accountId() + "/freeze")
                        .header("Authorization", customerToken(a))
                        .contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isForbidden());
        assertThat(bank.status(a)).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void staffCannotUseCustomerEndpoints() throws Exception {
        mvc.perform(get("/api/v1/accounts").header("Authorization", staffToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
    }

    @Test
    void staffCanFreezeAndUnfreezeWithAudit() throws Exception {
        TestAccount a = bank.open("Freeze Me", "0.00");
        String staff = staffToken();

        mvc.perform(patch("/api/v1/admin/accounts/" + a.accountId() + "/freeze").header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"))
                .andExpect(jsonPath("$.customerName").value("Freeze Me"));
        mvc.perform(patch("/api/v1/admin/accounts/" + a.accountId() + "/freeze").header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATUS_UNCHANGED"));
        assertThat(bank.status(a)).isEqualTo(AccountStatus.FROZEN);
        assertThat(jdbc.queryForObject("""
                SELECT payload->'before'->>'status' || '>' || (payload->'after'->>'status')
                FROM outbox_events WHERE aggregate_id = ? AND payload->>'action' = 'ACCOUNT_FREEZE'
                """, String.class, a.accountId())).isEqualTo("ACTIVE>FROZEN");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'ACCOUNT_STATUS_CHANGED'
                """, Integer.class, a.accountId())).isEqualTo(1);

        mvc.perform(patch("/api/v1/admin/accounts/" + a.accountId() + "/unfreeze").header("Authorization", staff)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND payload->>'action' = 'ACCOUNT_UNFREEZE'
                """, Integer.class, a.accountId())).isEqualTo(1);
    }

    @Test
    void freezeRequiresAReason() throws Exception {
        TestAccount a = bank.open("No Reason", "0.00");
        mvc.perform(patch("/api/v1/admin/accounts/" + a.accountId() + "/freeze").header("Authorization", staffToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void auditorCanReadButNeverMutate() throws Exception {
        TestAccount a = bank.open("Audited", "0.00");
        String auditor = auditorToken();

        mvc.perform(get("/api/v1/admin/accounts/" + a.accountId()).header("Authorization", auditor))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/admin/accounts/" + a.accountId() + "/freeze").header("Authorization", auditor)
                        .contentType(MediaType.APPLICATION_JSON).content(REASON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_OPERATION"));
        mvc.perform(put("/api/v1/admin/accounts/" + a.accountId() + "/limits").header("Authorization", auditor)
                        .contentType(MediaType.APPLICATION_JSON).content(LIMITS))
                .andExpect(status().isForbidden());

        assertThat(bank.status(a)).isEqualTo(AccountStatus.ACTIVE);
        mvc.perform(get("/api/v1/admin/accounts/" + a.accountId() + "/limits").header("Authorization", auditor))
                .andExpect(jsonPath("$.perTransactionLimit").value(100000000.00));
    }

    @Test
    void reconciliationIsForAuditorAndAdminOnly() throws Exception {
        TestAccount a = bank.open("Rec", "1000.00");
        TestAccount b = bank.open("Rec B", "0.00");
        String tx = JsonPath.read(mvc.perform(transfer(customerToken(a), newKey(), a.accountNumber(),
                b.accountNumber(), "10", null)).andReturn().getResponse().getContentAsString(), "$.transactionId");

        mvc.perform(get("/api/v1/admin/reconciliation/transactions/" + tx).header("Authorization", staffToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reconciliation/transactions/" + tx).header("Authorization", adminToken()))
                .andExpect(status().isOk());
    }
}
