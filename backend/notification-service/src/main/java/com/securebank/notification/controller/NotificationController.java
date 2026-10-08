package com.securebank.notification.controller;

import com.securebank.common.error.ApiError;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.CurrentUser;
import com.securebank.common.web.PageResponse;
import com.securebank.notification.application.NotificationQueryService;
import com.securebank.notification.application.dto.NotificationView;
import com.securebank.notification.application.dto.UnreadCount;
import com.securebank.notification.domain.Channel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "The caller's own notifications (recipient = JWT subject)")
@ApiResponse(responseCode = "401", description = "Missing or invalid access token",
        content = @Content(schema = @Schema(implementation = ApiError.class)))
public class NotificationController {

    private static final int MAX_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));

    private final NotificationQueryService queries;

    public NotificationController(NotificationQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "My notifications, newest first",
            description = "Any authenticated role. Optional channel filter (EMAIL, SMS, IN_APP) and unreadOnly.")
    @ApiResponse(responseCode = "200", description = "Page of notifications")
    @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public PageResponse<NotificationView> mine(@RequestParam(required = false) Channel channel,
                                               @RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "page must be >= 0 and size between 1 and 100.");
        }
        return queries.mine(CurrentUser.require().userId(), channel, unreadOnly, PageRequest.of(page, size, NEWEST_FIRST));
    }

    @GetMapping("/me/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Number of unread IN_APP notifications", description = "Any authenticated role.")
    public UnreadCount unreadCount() {
        return queries.unreadCount(CurrentUser.require().userId());
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark one of my notifications as read",
            description = "Owner only. Idempotent. Another user's notification answers 404 (existence is not revealed).")
    @ApiResponse(responseCode = "204", description = "Marked as read")
    @ApiResponse(responseCode = "404", description = "NOTIFICATION_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> markRead(@PathVariable UUID id) {
        queries.markRead(id, CurrentUser.require().userId());
        return ResponseEntity.noContent().build();
    }
}
