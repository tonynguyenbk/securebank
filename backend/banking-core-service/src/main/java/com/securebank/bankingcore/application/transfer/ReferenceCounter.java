package com.securebank.bankingcore.application.transfer;

import java.time.LocalDate;

/** Hands out the next per-day sequence number for transaction references. */
@FunctionalInterface
public interface ReferenceCounter {

    /** Next value (starting at 1) for the given business date; unique under concurrency. */
    long next(LocalDate businessDate);
}
