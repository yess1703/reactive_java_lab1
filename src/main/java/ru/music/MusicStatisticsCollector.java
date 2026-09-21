package ru.music;

import java.util.EnumMap;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public final class MusicStatisticsCollector
        implements Collector<Track, MusicStatisticsCollector.Accumulator, MusicStatistics> {
    public static final class Accumulator {
        private long count;
        private long duration;
        private long plays;
        private final EnumMap<Genre, Long> genres = new EnumMap<>(Genre.class);

        private void add(Track track) {
            count++;
            duration += track.durationSeconds();
            plays += track.playCount();
            genres.merge(track.genre(), 1L, Long::sum);
        }

        private Accumulator combine(Accumulator other) {
            count += other.count;
            duration += other.duration;
            plays += other.plays;
            other.genres.forEach((genre, value) -> genres.merge(genre, value, Long::sum));
            return this;
        }

        private MusicStatistics finish() {
            return new MusicStatistics(count, duration, plays, genres);
        }
    }

    @Override
    public Supplier<Accumulator> supplier() { return Accumulator::new; }

    @Override
    public BiConsumer<Accumulator, Track> accumulator() { return Accumulator::add; }

    @Override
    public BinaryOperator<Accumulator> combiner() { return Accumulator::combine; }

    @Override
    public Function<Accumulator, MusicStatistics> finisher() { return Accumulator::finish; }

    @Override
    public Set<Characteristics> characteristics() { return Set.of(Characteristics.UNORDERED); }
}
