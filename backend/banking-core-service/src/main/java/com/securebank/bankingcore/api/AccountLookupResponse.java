package com.securebank.bankingcore.api;

/** GET /accounts/lookup: beneficiary name check before sending. */
public record AccountLookupResponse(String accountNumber, String holderName, String currency) {
}
