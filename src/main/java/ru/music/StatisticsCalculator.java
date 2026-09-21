package ru.music;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class StatisticsCalculator {
    private StatisticsCalculator() { }

    public static MusicStatistics iterative(List<Track> tracks) {
        long count = 0;
        long duration = 0;
        long plays = 0;
        Map<Genre, Long> genres = new EnumMap<>(Genre.class);
        for (Track track : tracks) {
            count++;
            duration += track.durationSeconds();
            plays += track.playCount();
            genres.merge(track.genre(), 1L, Long::sum);
        }
        return new MusicStatistics(count, duration, plays, genres);
    }

    public static MusicStatistics standardCollectors(List<Track> tracks) {
        return tracks.stream().collect(Collectors.teeing(
                Collectors.summarizingLong(Track::durationSeconds),
                Collectors.teeing(
                        Collectors.summingLong(Track::playCount),
                        Collectors.groupingBy(Track::genre, () -> new EnumMap<>(Genre.class),
                                Collectors.counting()),
                        (plays, genres) -> new MusicStatistics(0, 0, plays, genres)),
                (durations, other) -> new MusicStatistics(durations.getCount(), durations.getSum(),
                        other.totalPlayCount(), other.tracksByGenre())));
    }

    public static MusicStatistics customCollector(List<Track> tracks) {
        return tracks.stream().collect(new MusicStatisticsCollector());
    }
}
