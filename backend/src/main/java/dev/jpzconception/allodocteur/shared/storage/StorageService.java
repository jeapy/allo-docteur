package dev.jpzconception.allodocteur.shared.storage;

import java.io.IOException;
import java.io.InputStream;

/** Port de stockage de fichiers. Implémentation locale par défaut ; S3 & co. pourront la remplacer. */
public interface StorageService {

    void store(String key, byte[] content) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}
