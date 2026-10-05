package ru.music;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

public record MusicStatistics(long trackCount, long totalDurationSeconds,
                              long totalPlayCount, Map<Genre, Long> tracksByGenre,
                              Map<String, Long> tracksByCountry) {
    public MusicStatistics {
        EnumMap<Genre, Long> copy = new EnumMap<>(Genre.class);
        for (Genre genre : Genre.values()) {
            copy.put(genre, tracksByGenre.getOrDefault(genre, 0L));
        }
        tracksByGenre = Collections.unmodifiableMap(copy);
        tracksByCountry = Collections.unmodifiableMap(new TreeMap<>(tracksByCountry));
    }

    public double averageDurationSeconds() {
        return trackCount == 0 ? 0.0 : (double) totalDurationSeconds / trackCount;
    }
}
