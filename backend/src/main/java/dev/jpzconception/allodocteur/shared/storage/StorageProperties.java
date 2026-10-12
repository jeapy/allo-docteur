package dev.jpzconception.allodocteur.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(@DefaultValue("./data/storage") String localPath) {}
