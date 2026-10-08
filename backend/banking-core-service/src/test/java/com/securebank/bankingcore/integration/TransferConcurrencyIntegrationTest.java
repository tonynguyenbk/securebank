package com.securebank.bankingcore.integration;

import com.securebank.bankingcore.support.IntegrationTestSupport;
import com.securebank.bankingcore.support.TestBank.TestAccount;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.securebank.bankingcore.support.TransferRequests.newKey;
import static com.securebank.bankingcore.support.TransferRequests.transfer;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec §11 / §36: real threads released together by a latch, each running its own HTTP request and database
 * transaction against PostgreSQL.
 */
class TransferConcurrencyIntegrationTest extends IntegrationTestSupport {

    @Test
    void twoSimultaneousTransfersCannotSpendTheSameBalance() throws Exception {
        TestAccount a = bank.open("Concurrent Alice", "1000000.00");
        TestAccount b = bank.open("Concurrent Bob", "0.00");
        TestAccount c = bank.open("Concurrent Carol", "0.00");
        String token = customerToken(a);

        List<MockHttpServletResponse> responses = runConcurrently(List.of(
                () -> mvc.perform(transfer(token, newKey(), a.accountNumber(), b.accountNumber(), "800000", null))
                        .andReturn().getResponse(),
                () -> mvc.perform(transfer(token, newKey(), a.accountNumber(), c.accountNumber(), "800000", null))
                        .andReturn().getResponse()));

        List<Integer> statuses = responses.stream().map(MockHttpServletResponse::getStatus).sorted().toList();
        assertThat(statuses).containsExactly(201, 422);
        MockHttpServletResponse rejected = responses.stream().filter(r -> r.getStatus() == 422).findFirst().orElseThrow();
        assertThat(rejected.getContentAsString()).contains("INSUFFICIENT_FUNDS");

        assertThat(bank.balance(a)).isEqualByComparingTo("200000");
        assertThat(bank.balance(b).add(bank.balance(c))).isEqualByComparingTo("800000");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM bank_transactions WHERE source_account_id = ? AND status = 'SUCCESS'
                """, Integer.class, a.accountId())).isEqualTo(1);
        // ledger consistent: source's running balance ends exactly at the account balance, never negative
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ledger_entries WHERE account_id = ? AND balance_after < 0
                """, Integer.class, a.accountId())).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT balance_after FROM ledger_entries WHERE account_id = ? AND entry_type = 'DEBIT'
                """, java.math.BigDecimal.class, a.accountId())).isEqualByComparingTo("200000");
        assertThat(jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN l.entry_type = 'DEBIT' THEN l.amount ELSE -l.amount END), 0)
                FROM ledger_entries l JOIN bank_transactions t ON t.id = l.transaction_id
                WHERE t.source_account_id = ?
                """, java.math.BigDecimal.class, a.accountId())).isEqualByComparingTo("0");
    }

    @Test
    void manyOppositeDirectionTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        TestAccount a = bank.open("Ping", "1000000.00");
        TestAccount b = bank.open("Pong", "1000000.00");
        List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean aToB = i % 2 == 0;
            TestAccount from = aToB ? a : b;
            TestAccount to = aToB ? b : a;
            tasks.add(() -> mvc.perform(transfer(customerToken(from), newKey(), from.accountNumber(),
                    to.accountNumber(), "1000", null)).andReturn().getResponse());
        }

        List<MockHttpServletResponse> responses = runConcurrently(tasks);

        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(201));
        assertThat(bank.balance(a).add(bank.balance(b))).isEqualByComparingTo("2000000");
        assertThat(bank.balance(a)).isEqualByComparingTo("1000000");
    }

    @Test
    void concurrentRequestsWithTheSameKeyMoveMoneyOnce() throws Exception {
        TestAccount a = bank.open("Double Click Alice", "5000000.00");
        TestAccount b = bank.open("Bob", "0.00");
        String token = customerToken(a);
        String key = newKey();
        List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> mvc.perform(transfer(token, key, a.accountNumber(), b.accountNumber(), "1000000", null))
                    .andReturn().getResponse());
        }

        List<MockHttpServletResponse> responses = runConcurrently(tasks);

        // every response is either the original 201 or its replay (or "still in progress" if the first took
        // longer than the lock timeout) — never a second transfer
        assertThat(responses).allSatisfy(r -> assertThat(r.getStatus()).isIn(201, 409));
        long fresh = responses.stream()
                .filter(r -> r.getStatus() == 201 && r.getHeader("Idempotent-Replayed") == null).count();
        assertThat(fresh).isEqualTo(1);
        String body = responses.stream().filter(r -> r.getStatus() == 201).findFirst().orElseThrow()
                .getContentAsString();
        for (MockHttpServletResponse r : responses) {
            if (r.getStatus() == 201) {
                assertThat(r.getContentAsString()).isEqualTo(body);
            }
        }
        assertThat(bank.balance(a)).isEqualByComparingTo("4000000");
        assertThat(bank.balance(b)).isEqualByComparingTo("1000000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bank_transactions WHERE source_account_id = ?",
                Integer.class, a.accountId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id = ?",
                Integer.class, a.accountId())).isEqualTo(1);
    }

    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
