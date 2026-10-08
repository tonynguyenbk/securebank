package com.securebank.bankingcore.application.query;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.domain.BankTransaction;
import org.springframework.data.jpa.domain.Specification;

/** Public entry to the transaction filter predicates for the admin list (all transactions, no visibility rule). */
public final class AdminTransactionQueries {

    private AdminTransactionQueries() {
    }

    public static Specification<BankTransaction> matching(TransactionFilter filter, BusinessCalendar calendar) {
        return TransactionSpecifications.matching(filter, calendar);
    }
}
