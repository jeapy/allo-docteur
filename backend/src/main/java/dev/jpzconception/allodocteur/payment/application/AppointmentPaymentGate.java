package dev.jpzconception.allodocteur.payment.application;

import dev.jpzconception.allodocteur.appointment.application.PaymentGate;
import dev.jpzconception.allodocteur.payment.domain.PayableType;
import dev.jpzconception.allodocteur.payment.domain.PaymentStatus;
import dev.jpzconception.allodocteur.payment.infrastructure.PaymentRepository;
import dev.jpzconception.allodocteur.shared.error.ApiException;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Un rendez-vous payant n'est confirmable qu'une fois les fonds reçus (en séquestre ou déjà répartis). */
@Component
@Transactional(readOnly = true)
public class AppointmentPaymentGate implements PaymentGate {

    private final PaymentRepository payments;
    private final PaymentProperties props;

    public AppointmentPaymentGate(PaymentRepository payments, PaymentProperties props) {
        this.payments = payments;
        this.props = props;
    }

    @Override
    public void requirePaid(UUID appointmentId, BigDecimal fee) {
        if (!props.requirePaymentToConfirm() || fee == null || fee.signum() <= 0) {
            return;
        }
        boolean paid = payments.existsByPayableTypeAndPayableIdAndStatusIn(PayableType.APPOINTMENT, appointmentId,
                EnumSet.of(PaymentStatus.CAPTURED, PaymentStatus.RELEASED));
        if (!paid) {
            throw ApiException.conflict("PAYMENT_REQUIRED",
                    "Le patient doit régler la consultation avant sa confirmation.");
        }
    }
}
