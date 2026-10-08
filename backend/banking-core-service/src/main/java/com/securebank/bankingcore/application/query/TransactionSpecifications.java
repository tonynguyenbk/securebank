package com.securebank.bankingcore.application.query;

import com.securebank.bankingcore.application.BusinessCalendar;
import com.securebank.bankingcore.domain.BankTransaction;
import com.securebank.bankingcore.domain.TransactionStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Criteria predicates for transaction lists; only non-null filters become predicates. */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * Customer visibility: any transaction sent from one of the caller's accounts, plus transactions received
     * by one of them that actually moved money (REJECTED attempts are visible to the sender only).
     */
    static Specification<BankTransaction> visibleTo(Collection<?> accountIds) {
        return (root, query, cb) -> cb.or(
                root.get("sourceAccount").get("id").in(accountIds),
                cb.and(root.get("destinationAccount").get("id").in(accountIds),
                        cb.notEqual(root.get("status"), TransactionStatus.REJECTED)));
    }

    static Specification<BankTransaction> matching(TransactionFilter filter, BusinessCalendar calendar) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.accountId() != null) {
                predicates.add(cb.or(cb.equal(root.get("sourceAccount").get("id"), filter.accountId()),
                        cb.equal(root.get("destinationAccount").get("id"), filter.accountId())));
            }
            if (filter.accountNumber() != null && !filter.accountNumber().isBlank()) {
                String number = filter.accountNumber().trim();
                predicates.add(cb.or(cb.equal(root.get("sourceAccount").get("accountNumber"), number),
                        cb.equal(root.get("destinationAccountNumber"), number)));
            }
            if (filter.reference() != null && !filter.reference().isBlank()) {
                predicates.add(cb.equal(root.get("transactionReference"), filter.reference().trim()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.fromDate() != null || filter.toDate() != null) {
                BusinessCalendar.Range range = calendar.between(filter.fromDate(), filter.toDate());
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), range.from()));
                predicates.add(cb.lessThan(root.get("createdAt"), range.to()));
            }
            if (filter.minAmount() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
