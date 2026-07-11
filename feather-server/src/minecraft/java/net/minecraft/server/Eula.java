package net.minecraft.server;

import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.minecraft.SharedConstants;
import net.minecraft.util.CommonLinks;
import org.slf4j.Logger;

public class Eula {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path file;
    private final boolean agreed;

    public Eula(final Path file) {
        this.file = file;
        this.agreed = SharedConstants.IS_RUNNING_IN_IDE || this.readGlobalFile() || this.readFile(); // Gale - YAPFA - global EULA file
    }

    private boolean readFile() {
        // Gale start - YAPFA - global EULA file
        return readFile(this.file);
    }
    private boolean readGlobalFile() {
        try {
            Path globalFile = Path.of(System.getProperty("user.home"), "eula.txt");

            if (globalFile.toFile().exists()) {
                return readFile(globalFile);
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
    private boolean readFile(Path file) {
        // Gale end - YAPFA - global EULA file
        try (InputStream input = Files.newInputStream(file)) { // Gale - YAPFA - global EULA file
            Properties properties = new Properties();
            properties.load(input);
            return Boolean.parseBoolean(properties.getProperty("eula", "false"));
        } catch (Exception ignored) {
            if (file == this.file) { // Gale - YAPFA - global EULA file
            LOGGER.warn("Failed to load {}", this.file);
            this.saveDefaults();
            } // Gale - YAPFA - global EULA file
            return false;
        }
    }

    public boolean hasAgreedToEULA() {
        return this.agreed;
    }

    private void saveDefaults() {
        if (!SharedConstants.IS_RUNNING_IN_IDE) {
            try (OutputStream output = Files.newOutputStream(this.file)) {
                Properties properties = new Properties();
                properties.setProperty("eula", "false");
                properties.store(output, "By changing the setting below to TRUE you are indicating your agreement to our EULA (" + CommonLinks.EULA + ").");
            } catch (Exception e) {
                LOGGER.warn("Failed to save {}", this.file, e);
            }
        }
    }
}
