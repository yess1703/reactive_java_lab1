package ru.music.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import ru.music.Album;
import ru.music.Artist;
import ru.music.MusicGenerator;
import ru.music.MusicStatistics;
import ru.music.StatisticsCalculator;
import ru.music.Track;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class StatisticsBenchmark {
    @Param({"0"})
    public long delayMillis;

    @Param({"1"})
    public int size;

    private List<Track> tracks;
    private ForkJoinPool pool;

    @Setup(Level.Trial)
    public void setup() {
        MusicGenerator generator = new MusicGenerator(42L);
        List<Artist> artists = generator.generateArtists(100);
        List<Album> albums = generator.generateAlbums(500, artists);
        tracks = generator.generateTracks(size, albums);
        pool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
    }

    @TearDown(Level.Trial)
    public void tearDown() throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
            pool.shutdownNow();
        }
    }

    @Benchmark
    public MusicStatistics sequential() {
        return StatisticsCalculator.sequential(tracks, delayMillis);
    }

    @Benchmark
    public MusicStatistics parallel() {
        return StatisticsCalculator.parallel(tracks, delayMillis);
    }

    @Benchmark
    public MusicStatistics forkJoin() {
        return StatisticsCalculator.forkJoin(tracks, delayMillis, pool);
    }
}
