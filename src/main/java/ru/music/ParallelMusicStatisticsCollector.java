package ru.music;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class ParallelMusicStatisticsCollector
        implements Collector<Track, ParallelMusicStatisticsCollector.Accumulator, MusicStatistics> {
    private final long delayMillis;

    public ParallelMusicStatisticsCollector(long delayMillis) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("Задержка не может быть отрицательной");
        }
        this.delayMillis = delayMillis;
    }

    public static final class Accumulator {
        private final LongAdder count = new LongAdder();
        private final LongAdder duration = new LongAdder();
        private final LongAdder plays = new LongAdder();
        private final ConcurrentHashMap<Genre, LongAdder> genres = new ConcurrentHashMap<>();
        private final ConcurrentSkipListMap<String, LongAdder> countries = new ConcurrentSkipListMap<>();

        void add(Track track, long delayMillis) {
            String country = track.artist().country(delayMillis);
            count.increment();
            duration.add(track.durationSeconds());
            plays.add(track.playCount());
            genres.computeIfAbsent(track.genre(), key -> new LongAdder()).increment();
            countries.computeIfAbsent(country, key -> new LongAdder()).increment();
        }

        Accumulator combine(Accumulator other) {
            count.add(other.count.sum());
            duration.add(other.duration.sum());
            plays.add(other.plays.sum());
            other.genres.forEach((genre, value) ->
                    genres.computeIfAbsent(genre, key -> new LongAdder()).add(value.sum()));
            other.countries.forEach((country, value) ->
                    countries.computeIfAbsent(country, key -> new LongAdder()).add(value.sum()));
            return this;
        }

        MusicStatistics finish() {
            Map<Genre, Long> genreCounts = new EnumMap<>(Genre.class);
            genres.forEach((genre, value) -> genreCounts.put(genre, value.sum()));
            Map<String, Long> countryCounts = new TreeMap<>();
            countries.forEach((country, value) -> countryCounts.put(country, value.sum()));
            return new MusicStatistics(count.sum(), duration.sum(), plays.sum(), genreCounts, countryCounts);
        }
    }

    @Override
    public Supplier<Accumulator> supplier() { return Accumulator::new; }

    @Override
    public BiConsumer<Accumulator, Track> accumulator() {
        long delay = delayMillis;
        return (accumulator, track) -> accumulator.add(track, delay);
    }

    @Override
    public BinaryOperator<Accumulator> combiner() { return Accumulator::combine; }

    @Override
    public Function<Accumulator, MusicStatistics> finisher() { return Accumulator::finish; }

    @Override
    public Set<Characteristics> characteristics() {
        return Set.of(Characteristics.CONCURRENT, Characteristics.UNORDERED);
    }
}
