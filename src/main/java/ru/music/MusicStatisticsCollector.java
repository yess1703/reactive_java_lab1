package ru.music;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class MusicStatisticsCollector
        implements Collector<Track, MusicStatisticsCollector.Accumulator, MusicStatistics> {
    private final long delayMillis;

    public MusicStatisticsCollector() {
        this(0L);
    }

    public MusicStatisticsCollector(long delayMillis) {
        if (delayMillis < 0) {
            throw new IllegalArgumentException("Задержка не может быть отрицательной");
        }
        this.delayMillis = delayMillis;
    }

    public static final class Accumulator {
        private long count;
        private long duration;
        private long plays;
        private final EnumMap<Genre, Long> genres = new EnumMap<>(Genre.class);
        private final Map<String, Long> countries = new TreeMap<>();

        void add(Track track, long delayMillis) {
            count++;
            duration += track.durationSeconds();
            plays += track.playCount();
            genres.merge(track.genre(), 1L, Long::sum);
            countries.merge(track.artist().country(delayMillis), 1L, Long::sum);
        }

        Accumulator combine(Accumulator other) {
            count += other.count;
            duration += other.duration;
            plays += other.plays;
            other.genres.forEach((genre, value) -> genres.merge(genre, value, Long::sum));
            other.countries.forEach((country, value) -> countries.merge(country, value, Long::sum));
            return this;
        }

        MusicStatistics finish() {
            return new MusicStatistics(count, duration, plays, genres, countries);
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
    public Set<Characteristics> characteristics() { return Set.of(Characteristics.UNORDERED); }
}
