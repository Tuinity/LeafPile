package ca.spottedleaf.yamlconfig.config;

import ca.spottedleaf.yamlconfig.adapter.TypeAdapterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.concurrent.locks.StampedLock;

public final class ConfigHolder<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigHolder.class);

    private final StampedLock configLock = new StampedLock();
    private final File configFile;
    private final YamlConfig<T> config;
    private final String configHeader;

    public ConfigHolder(final File configFile, final Class<? extends T> clazz, final T dfl) throws Exception {
        this(configFile, clazz, dfl, new TypeAdapterRegistry(), "");
    }

    public ConfigHolder(final File configFile, final Class<? extends T> clazz, final T dfl, final TypeAdapterRegistry registry,
                        final String configHeader) throws Exception {
        this.configFile = configFile;
        this.config = new YamlConfig<>(clazz, dfl, registry);
        this.configHeader = configHeader;
    }

    public boolean reload(final boolean startup) {
        return this.reload(startup, LOGGER);
    }

    public boolean reload(final boolean startup, final Logger logger) {
        final long lock = this.configLock.writeLock();
        try {
            if (this.configFile.exists()) {
                try {
                    this.config.load(this.configFile);
                } catch (final Exception ex) {
                    if (startup) {
                        logger.error("Failed to load configuration, using defaults", ex);
                        this.config.callInitialisers();
                    } else {
                        logger.error("Failed to reload configuration", ex);
                    }
                    return false;
                }
            }

            this.config.callInitialisers();
        } finally {
            this.configLock.unlockWrite(lock);
        }

        // write back any changes, or create if needed
        return this.save(logger);
    }

    public boolean save() {
        return this.save(LOGGER);
    }

    public boolean save(final Logger logger) {
        final long lock = this.configLock.writeLock();
        try {
            try {
                this.config.save(this.configFile, this.configHeader);
                return true;
            } catch (final Exception ex) {
                logger.error("Failed to save configuration", ex);
                return false;
            }
        } finally {
            this.configLock.unlockWrite(lock);
        }
    }

    public T getConfig() {
        // use the lock to order initialisers
        final long optimisticRead = this.configLock.tryOptimisticRead();
        final T optimistic = this.config.config;
        if (this.configLock.validate(optimisticRead)) {
            return optimistic;
        }

        final long lock = this.configLock.readLock();
        try {
            return this.config.config;
        } finally {
            this.configLock.unlockRead(lock);
        }
    }
}
