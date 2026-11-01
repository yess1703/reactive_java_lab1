package ru.music.benchmark;

import org.openjdk.jmh.annotations.*;
import ru.music.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 2, jvmArgsAppend = {"-Xms128m", "-Xmx256m"})
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Threads(1)
@State(Scope.Benchmark)
public class ReactiveStatisticsBenchmark {
    @Param({"500", "2500"}) public int size;
    @Param({"1"}) public long delayMillis;
    private List<Track> tracks;
    private ReactiveStatistics reactive;

    @Setup(Level.Trial)
    public void setup() {
        MusicGenerator generator = new MusicGenerator(42);
        var albums = generator.generateAlbums(500, generator.generateArtists(100));
        tracks = generator.generateTracks(size, albums);
        reactive = new ReactiveStatistics(ReactiveStatistics.defaultWorkers());
        var expected = StatisticsCalculator.iterative(tracks);
        if (!expected.equals(reactive.observable(tracks, delayMillis).blockingGet())
                || !expected.equals(StatisticsCalculator.parallel(tracks, delayMillis))) {
            throw new IllegalStateException("Несовпадение статистики");
        }
    }

    @TearDown(Level.Trial)
    public void close() { reactive.close(); }

    @Benchmark
    public MusicStatistics parallel() {
        return StatisticsCalculator.parallel(tracks, delayMillis);
    }

    @Benchmark
    public MusicStatistics observable() {
        // Ожидание включено в замер: сравниваем завершённые расчёты, не создание цепочки.
        return reactive.observable(tracks, delayMillis).blockingGet();
    }
}
