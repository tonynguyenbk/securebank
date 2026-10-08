package com.securebank.bankingcore.repository;

import com.securebank.bankingcore.domain.Customer;

/** Customer row plus its account count, loaded in one query (no N+1 on the admin customer list). */
public record CustomerWithAccountCount(Customer customer, long accountCount) {
}
