package dev.jpzconception.allodocteur.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

/** Stockage sur disque local, avec protection contre la traversée de répertoires. */
@Component
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(StorageProperties props) {
        this.root = Path.of(props.localPath()).toAbsolutePath().normalize();
    }

    @Override
    public void store(String key, byte[] content) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Clé de stockage invalide");
        }
        return p;
    }
}
