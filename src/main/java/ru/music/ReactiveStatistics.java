package ru.music;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Observable: ограниченное число независимых задач и локальные накопители. */
public final class ReactiveStatistics implements AutoCloseable {
    private final int workers;
    private final ExecutorService executor;
    private final Scheduler scheduler;

    public ReactiveStatistics(int workers) {
        if (workers <= 0) throw new IllegalArgumentException("workers должен быть положительным");
        this.workers = workers;
        AtomicInteger ids = new AtomicInteger();
        executor = Executors.newFixedThreadPool(workers, task -> {
            Thread thread = new Thread(task, "lab3-observable-" + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        scheduler = Schedulers.from(executor, true);
    }

    public static int defaultWorkers() {
        // Блокирующее ожидание допускает больше потоков, чем вычислительная задача.
        return Math.min(128, Math.max(32, Runtime.getRuntime().availableProcessors() * 4));
    }

    public Single<MusicStatistics> observable(List<Track> tracks, long delayMillis) {
        Objects.requireNonNull(tracks, "tracks");
        if (delayMillis < 0) throw new IllegalArgumentException("Отрицательная задержка");
        return Single.defer(() -> {
            int lanes = Math.min(workers, tracks.size());
            return Observable.range(0, lanes)
                    .flatMap(lane -> Observable.fromCallable(() -> {
                        var local = new MusicStatisticsCollector.Accumulator();
                        for (int index = lane; index < tracks.size(); index += lanes) {
                            if (Thread.currentThread().isInterrupted()) {
                                throw new InterruptedException("Сбор отменён");
                            }
                            local.add(tracks.get(index), delayMillis);
                        }
                        return local;
                    }).subscribeOn(scheduler), false, workers)
                    // flatMap сериализует события вниз по цепочке: общий итог не требует lock.
                    .collect(MusicStatisticsCollector.Accumulator::new,
                            (total, part) -> total.combine(part))
                    .map(MusicStatisticsCollector.Accumulator::finish);
        });
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException error) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
