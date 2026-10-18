package ru.music;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class StatisticsCalculator {
    private StatisticsCalculator() { }

    public static MusicStatistics iterative(List<Track> tracks) {
        long count = 0;
        long duration = 0;
        long plays = 0;
        Map<Genre, Long> genres = new EnumMap<>(Genre.class);
        Map<String, Long> countries = new TreeMap<>();
        for (Track track : tracks) {
            count++;
            duration += track.durationSeconds();
            plays += track.playCount();
            genres.merge(track.genre(), 1L, Long::sum);
            countries.merge(track.artist().country(), 1L, Long::sum);
        }
        return new MusicStatistics(count, duration, plays, genres, countries);
    }

    public static MusicStatistics standardCollectors(List<Track> tracks) {
        return tracks.stream().collect(Collectors.teeing(
                Collectors.summarizingLong(Track::durationSeconds),
                Collectors.teeing(
                        Collectors.teeing(
                                Collectors.summingLong(Track::playCount),
                                Collectors.groupingBy(Track::genre, () -> new EnumMap<>(Genre.class),
                                        Collectors.counting()),
                                (plays, genres) -> new MusicStatistics(0, 0, plays, genres, Map.of())),
                        Collectors.groupingBy(track -> track.artist().country(), TreeMap::new,
                                Collectors.counting()),
                        (partial, countries) -> new MusicStatistics(partial.trackCount(),
                                partial.totalDurationSeconds(), partial.totalPlayCount(),
                                partial.tracksByGenre(), countries)),
                (durations, other) -> new MusicStatistics(durations.getCount(), durations.getSum(),
                        other.totalPlayCount(), other.tracksByGenre(), other.tracksByCountry())));
    }

    public static MusicStatistics customCollector(List<Track> tracks) {
        return tracks.stream().collect(new MusicStatisticsCollector());
    }

    public static MusicStatistics sequential(List<Track> tracks, long delayMillis) {
        return tracks.stream().collect(new MusicStatisticsCollector(delayMillis));
    }

    public static MusicStatistics parallel(List<Track> tracks, long delayMillis) {
        return tracks.parallelStream().collect(new ParallelMusicStatisticsCollector(delayMillis));
    }

    public static MusicStatistics forkJoin(List<Track> tracks, long delayMillis) {
        int parallelism = Runtime.getRuntime().availableProcessors();
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        try {
            return forkJoin(tracks, delayMillis, pool);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException error) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static MusicStatistics forkJoin(List<Track> tracks, long delayMillis, ForkJoinPool pool) {
        return ForkJoinStatistics.collect(tracks, delayMillis, pool);
    }
}
