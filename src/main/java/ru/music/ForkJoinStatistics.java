package ru.music;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public final class ForkJoinStatistics {
    private ForkJoinStatistics() { }

    public static MusicStatistics collect(List<Track> tracks, long delayMillis, ForkJoinPool pool) {
        Objects.requireNonNull(tracks, "tracks");
        Objects.requireNonNull(pool, "pool");
        if (delayMillis < 0) {
            throw new IllegalArgumentException("Задержка не может быть отрицательной");
        }
        if (tracks.isEmpty()) {
            return new MusicStatistics(0, 0, 0, Map.of(), Map.of());
        }
        int leafSize = leafSize(tracks.size(), delayMillis, pool.getParallelism());
        return pool.invoke(new Task(tracks, 0, tracks.size(), delayMillis, leafSize)).finish();
    }

    static int leafSize(int size, long delayMillis, int parallelism) {
        int workers = Math.max(1, parallelism);
        if (delayMillis > 0) {
            return Math.max(1, size / (workers * 4));
        }
        return Math.max(512, size / (workers * 8));
    }

    private static final class Task extends RecursiveTask<MusicStatisticsCollector.Accumulator> {
        private final List<Track> tracks;
        private final int start;
        private final int end;
        private final long delayMillis;
        private final int leafSize;

        private Task(List<Track> tracks, int start, int end, long delayMillis, int leafSize) {
            this.tracks = tracks;
            this.start = start;
            this.end = end;
            this.delayMillis = delayMillis;
            this.leafSize = leafSize;
        }

        @Override
        protected MusicStatisticsCollector.Accumulator compute() {
            if (end - start <= leafSize) {
                MusicStatisticsCollector.Accumulator accumulator = new MusicStatisticsCollector.Accumulator();
                for (int index = start; index < end; index++) {
                    accumulator.add(tracks.get(index), delayMillis);
                }
                return accumulator;
            }
            int middle = start + ((end - start) >>> 1);
            Task right = new Task(tracks, middle, end, delayMillis, leafSize);
            right.fork();
            MusicStatisticsCollector.Accumulator left =
                    new Task(tracks, start, middle, delayMillis, leafSize).compute();
            return left.combine(right.join());
        }
    }
}
