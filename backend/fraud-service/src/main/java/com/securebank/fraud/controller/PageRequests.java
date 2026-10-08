package com.securebank.fraud.controller;

import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Builds a {@link Pageable} from the contract's {@code page/size/sort} parameters (api.md §0). */
final class PageRequests {

    static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /**
     * @param sort          {@code field,asc|desc}; null = default
     * @param sortableFields whitelist (unknown fields would otherwise surface as a 500)
     */
    static Pageable of(int page, int size, String sort, Set<String> sortableFields, Sort defaultSort) {
        if (page < 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be >= 0.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "size must be between 1 and " + MAX_SIZE + ".");
        }
        return PageRequest.of(page, size, parseSort(sort, sortableFields, defaultSort));
    }

    private static Sort parseSort(String sort, Set<String> sortableFields, Sort defaultSort) {
        if (sort == null || sort.isBlank()) {
            return defaultSort;
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!sortableFields.contains(field) || parts.length > 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "sort must be one of " + sortableFields + " optionally followed by ,asc or ,desc.");
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = Sort.Direction.fromOptionalString(parts[1].trim())
                    .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "sort direction must be asc or desc."));
        }
        // stable secondary order so pages don't overlap when the primary key ties
        return Sort.by(direction, field).and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
