package ru.music;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public record MusicStatistics(long trackCount, long totalDurationSeconds,
                              long totalPlayCount, Map<Genre, Long> tracksByGenre) {
    public MusicStatistics {
        EnumMap<Genre, Long> copy = new EnumMap<>(Genre.class);
        for (Genre genre : Genre.values()) {
            copy.put(genre, tracksByGenre.getOrDefault(genre, 0L));
        }
        tracksByGenre = Collections.unmodifiableMap(copy);
    }

    public double averageDurationSeconds() {
        return trackCount == 0 ? 0.0 : (double) totalDurationSeconds / trackCount;
    }
}
