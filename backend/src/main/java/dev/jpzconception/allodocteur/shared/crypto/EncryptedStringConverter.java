package dev.jpzconception.allodocteur.shared.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/** Chiffre/déchiffre une colonne texte de façon transparente (@Convert). */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final AesGcmCrypto crypto;

    public EncryptedStringConverter(AesGcmCrypto crypto) {
        this.crypto = crypto;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : crypto.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : crypto.decrypt(dbData);
    }
}
