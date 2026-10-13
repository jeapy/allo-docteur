package dev.jpzconception.allodocteur.pharmacy.application;

import java.math.BigDecimal;

/**
 * Port vers un prestataire de livraison (coursier, plateforme de livraison…). Une seule implémentation est factice
 * pour l'instant ; en brancher une vraie ne demande aucun changement dans le module pharmacie.
 */
public interface DeliveryProvider {

    record Request(String orderNumber, String pharmacyName, String pickupAddress, String dropAddress,
                   BigDecimal dropLatitude, BigDecimal dropLongitude, String contactPhone) {}

    record Dispatch(String provider, String trackingCode, String courierName, String courierPhone) {}

    Dispatch dispatch(Request request);
}
