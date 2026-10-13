package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.payment.domain.TxReason;
import dev.jpzconception.allodocteur.payment.domain.TxType;
import dev.jpzconception.allodocteur.payment.domain.Wallet;
import dev.jpzconception.allodocteur.payment.domain.WalletOwnerType;
import dev.jpzconception.allodocteur.payment.domain.WalletTransaction;
import dev.jpzconception.allodocteur.payment.infrastructure.WalletRepository;
import dev.jpzconception.allodocteur.payment.infrastructure.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seul point d'écriture des soldes : chaque mouvement verrouille le portefeuille, met le solde à jour et ajoute une ligne
 * au grand livre dans la même transaction.
 */
@Service
@Transactional
public class WalletService {

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final Clock clock;

    public WalletService(WalletRepository wallets, WalletTransactionRepository transactions, Clock clock) {
        this.wallets = wallets;
        this.transactions = transactions;
        this.clock = clock;
    }

    public WalletTransaction credit(WalletOwnerType type, UUID ownerId, BigDecimal amount, TxReason reason,
                                    UUID paymentId, UUID withdrawalId, String description) {
        Wallet w = lock(type, ownerId);
        w.credit(amount);
        return append(w, TxType.CREDIT, reason, amount, paymentId, withdrawalId, description);
    }

    public WalletTransaction debit(WalletOwnerType type, UUID ownerId, BigDecimal amount, TxReason reason,
                                   UUID paymentId, UUID withdrawalId, String description) {
        Wallet w = lock(type, ownerId);
        w.debit(amount); // 409 INSUFFICIENT_FUNDS
        return append(w, TxType.DEBIT, reason, amount, paymentId, withdrawalId, description);
    }

    /** Identifiant du portefeuille d'un utilisateur ou d'une pharmacie, créé au besoin (sans le charger). */
    public UUID ensureWallet(WalletOwnerType type, UUID ownerId) {
        wallets.insertIfAbsent(UUID.randomUUID(), type.name(), ownerId);
        return wallets.findIdByOwner(type, ownerId).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Summary summary(WalletOwnerType type, UUID ownerId) {
        return find(type, ownerId).map(w -> new Summary(w.getId(), w.getBalance(), w.getCurrency()))
                .orElse(new Summary(null, BigDecimal.ZERO, "XOF"));
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> transactions(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return find(WalletOwnerType.USER, userId)
                .map(w -> transactions.findByWalletIdOrderByCreatedAtDesc(w.getId(), pageable))
                .orElse(Page.empty(pageable));
    }

    public record Summary(UUID walletId, BigDecimal balance, String currency) {}

    private java.util.Optional<Wallet> find(WalletOwnerType type, UUID ownerId) {
        return type == WalletOwnerType.PLATFORM
                ? wallets.findByOwnerTypeAndOwnerIdIsNull(type)
                : wallets.findByOwnerTypeAndOwnerId(type, ownerId);
    }

    private Wallet lock(WalletOwnerType type, UUID ownerId) {
        if (type == WalletOwnerType.PLATFORM) {
            return wallets.lockPlatform(type).orElseThrow();
        }
        wallets.insertIfAbsent(UUID.randomUUID(), type.name(), ownerId);
        return wallets.lockByOwner(type, ownerId).orElseThrow();
    }

    private WalletTransaction append(Wallet w, TxType type, TxReason reason, BigDecimal amount, UUID paymentId,
                                     UUID withdrawalId, String description) {
        WalletTransaction t = new WalletTransaction();
        t.setWalletId(w.getId());
        t.setType(type);
        t.setReason(reason);
        t.setAmount(amount);
        t.setBalanceAfter(w.getBalance());
        t.setPaymentId(paymentId);
        t.setWithdrawalId(withdrawalId);
        t.setDescription(description);
        t.setCreatedAt(clock.instant());
        return transactions.save(t);
    }
}
