package util.support;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Совместимый thread-safe генератор configVersion для splitter-тестов.
 *
 * Поддерживает оба стиля вызовов:
 * - next()
 * - nextVersion()
 *
 * Также содержит методы для сценариев со "старой" версией.
 */
public final class SplitterVersionProvider {

    private static final AtomicLong SEQUENCE =
            new AtomicLong(System.currentTimeMillis() * 100L);

    private SplitterVersionProvider() {
        // utility class
    }

    /**
     * Legacy-compatible alias.
     */
    public static long next() {
        return nextVersion();
    }

    /**
     * Возвращает одну уникальную и монотонно возрастающую версию.
     */
    public static long nextVersion() {
        return SEQUENCE.incrementAndGet();
    }

    /**
     * Возвращает версию, которая гарантированно меньше переданной.
     */
    public static long nextOlderThan(long newerVersion) {
        return newerVersion - 1L;
    }

    /**
     * Резервирует две соседние версии и возвращает их как пару old/new.
     */
    public static OrderedVersions nextOrderedVersions() {
        long end = SEQUENCE.addAndGet(2L);
        long oldVersion = end - 1L;
        long newVersion = end;
        return new OrderedVersions(oldVersion, newVersion);
    }

    /**
     * Резервирует две соседние версии и возвращает их как пару forcedOld/new.
     */
    public static ForcedOrderedVersions nextForcedOrderedVersions() {
        long end = SEQUENCE.addAndGet(2L);
        long forcedOldVersion = end - 1L;
        long newVersion = end;
        return new ForcedOrderedVersions(forcedOldVersion, newVersion);
    }

    /**
     * Возвращает count последовательных версий.
     */
    public static long[] nextVersions(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
        long[] versions = new long[count];
        for (int i = 0; i < count; i++) {
            versions[i] = nextVersion();
        }
        return versions;
    }

    public static final class OrderedVersions {
        private final long oldVersion;
        private final long newVersion;

        public OrderedVersions(long oldVersion, long newVersion) {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        public long getOldVersion() {
            return oldVersion;
        }

        public long getNewVersion() {
            return newVersion;
        }
    }

    public static final class ForcedOrderedVersions {
        private final long forcedOldVersion;
        private final long newVersion;

        public ForcedOrderedVersions(long forcedOldVersion, long newVersion) {
            this.forcedOldVersion = forcedOldVersion;
            this.newVersion = newVersion;
        }

        public long getForcedOldVersion() {
            return forcedOldVersion;
        }

        public long getNewVersion() {
            return newVersion;
        }
    }
}
