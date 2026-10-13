package dev.jpzconception.allodocteur.notification.api;

import dev.jpzconception.allodocteur.notification.application.NotificationService;
import dev.jpzconception.allodocteur.notification.domain.Notification;
import dev.jpzconception.allodocteur.notification.domain.NotificationType;
import dev.jpzconception.allodocteur.shared.security.AuthenticatedUser;
import dev.jpzconception.allodocteur.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Notifications in-app du patient ou du professionnel (interrogation périodique par l'application mobile). */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('PATIENT', 'PROFESSIONAL')")
@Tag(name = "Notifications")
public class NotificationController {

    public record NotificationDto(UUID id, NotificationType type, String title, String body, String refType, UUID refId,
                                  boolean read, Instant createdAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getRefType(), n.getRefId(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public PageResponse<NotificationDto> list(@AuthenticationPrincipal AuthenticatedUser me,
                                              @RequestParam(defaultValue = "false") boolean unread,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return PageResponse.of(notifications.list(me.id(), unread, page, size).map(NotificationDto::of));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of("count", notifications.unreadCount(me.id()));
    }

    @PostMapping("/{id}/read")
    public NotificationDto read(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable UUID id) {
        return NotificationDto.of(notifications.markRead(me.id(), id));
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of("updated", notifications.markAllRead(me.id()));
    }
}
