package com.securebank.bankingcore.application.query;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Set;

/**
 * Builds {@link Pageable}s from the contract's {@code page}, {@code size} (max 100) and {@code sort=field,dir}
 * parameters with a whitelist of sortable fields, so clients cannot sort on arbitrary (or unindexed) paths.
 */
public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    public static Pageable of(int page, int size, String sort, Set<String> allowedFields, Sort defaultSort) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be >= 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "size must be between 1 and " + MAX_SIZE);
        }
        return PageRequest.of(page, size, parseSort(sort, allowedFields, defaultSort));
    }

    public static Pageable unsorted(int page, int size) {
        return of(page, size, null, Set.of(), Sort.unsorted());
    }

    static Sort parseSort(String sort, Set<String> allowedFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!allowedFields.contains(field)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "sort field must be one of " + String.join(", ", allowedFields));
        }
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1) {
            String dir = parts[1].trim().toUpperCase(Locale.ROOT);
            if (!dir.equals("ASC") && !dir.equals("DESC")) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "sort direction must be asc or desc");
            }
            direction = Sort.Direction.valueOf(dir);
        }
        // stable secondary order for deterministic paging
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    /** Lower-case LIKE pattern "%q%" with '!' as escape character; "%" (match all) for blank input. */
    public static String containsPattern(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        String escaped = q.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
