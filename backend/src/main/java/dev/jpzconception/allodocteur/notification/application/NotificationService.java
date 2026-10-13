package dev.jpzconception.allodocteur.notification.application;

import dev.jpzconception.allodocteur.notification.application.NotificationChannel.Recipient;
import dev.jpzconception.allodocteur.notification.domain.Notification;
import dev.jpzconception.allodocteur.notification.domain.NotificationType;
import dev.jpzconception.allodocteur.notification.infrastructure.NotificationRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import dev.jpzconception.allodocteur.user.infrastructure.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Crée les notifications in-app et, pour certains types, les envoie par les canaux externes une fois la transaction
 * validée (jamais d'envoi pour une opération annulée).
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final List<NotificationChannel> channels;
    private final NotificationProperties props;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, UserRepository users,
                               List<NotificationChannel> channels, NotificationProperties props, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.channels = channels;
        this.props = props;
        this.clock = clock;
    }

    public void notify(UUID userId, NotificationType type, String title, String body, String refType, UUID refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setCreatedAt(clock.instant());
        notifications.save(n);

        if (props.externalTypes().contains(type)) {
            users.findById(userId).ifPresent(u -> {
                Recipient r = new Recipient(u.getId(), u.getEmail(), u.getPhone());
                afterCommit(() -> dispatch(r, title, body));
            });
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(UUID userId, boolean unreadOnly, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return unreadOnly
                ? notifications.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : notifications.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    public Notification markRead(UUID userId, UUID id) {
        Notification n = notifications.findById(id).filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification introuvable."));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
        }
        return n;
    }

    public int markAllRead(UUID userId) {
        return notifications.markAllRead(userId, clock.instant());
    }

    private void dispatch(Recipient r, String title, String body) {
        for (NotificationChannel c : channels) {
            try {
                if (c.canReach(r)) {
                    c.send(r, title, body);
                }
            } catch (RuntimeException e) {
                log.warn("Envoi {} impossible pour {} : {}", c.code(), r.userId(), e.getMessage());
            }
        }
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
