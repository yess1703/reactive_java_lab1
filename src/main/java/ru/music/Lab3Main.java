package ru.music;

import io.reactivex.rxjava3.schedulers.Schedulers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class Lab3Main {
    private Lab3Main() { }

    public static void main(String[] args) throws Exception {
        List<String> report = new ArrayList<>();
        report.add("Flowable без задержки: генерация по запросу, Subscriber запрашивает порции по 256.");
        report.add("observeOn prefetch = 128; весь список треков в памяти не хранится.");
        report.add("Java: " + System.getProperty("java.version") + "; максимальная куча: "
                + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MiB");
        var producer = Executors.newSingleThreadExecutor(task -> new Thread(task, "lab3-producer"));
        var consumer = Executors.newSingleThreadExecutor(task -> new Thread(task, "lab3-consumer"));
        try {
            for (int size : new int[]{250_000, 1_000_000}) {
                AtomicLong produced = new AtomicLong();
                AtomicLong consumed = new AtomicLong();
                AtomicLong maxPending = new AtomicLong();
                long start = System.nanoTime();
                try (var subscriber = new StatisticsSubscriber(256)) {
                    ReactiveTrackSource.generate(size, 42, Schedulers.from(producer))
                            .doOnNext(track -> {
                                long pending = produced.incrementAndGet() - consumed.get();
                                maxPending.accumulateAndGet(pending, Math::max);
                            })
                            .observeOn(Schedulers.from(consumer), false, 128)
                            .doAfterNext(track -> consumed.incrementAndGet())
                            .subscribe(subscriber);
                    MusicStatistics stats = subscriber.await(Duration.ofSeconds(60));
                    long byCountry = stats.tracksByCountry().values().stream().mapToLong(Long::longValue).sum();
                    long byGenre = stats.tracksByGenre().values().stream().mapToLong(Long::longValue).sum();
                    if (stats.trackCount() != size || produced.get() != size
                            || byCountry != size || byGenre != size || maxPending.get() > 129) {
                        throw new AssertionError("Потеря элементов или неограниченный буфер");
                    }
                    double millis = (System.nanoTime() - start) / 1_000_000.0;
                    report.add(String.format(java.util.Locale.US,
                            "%,d треков: %.3f мс; максимум созданных, но ещё не обработанных: %d; OK",
                            size, millis, maxPending.get()));
                }
            }
        } finally {
            producer.shutdownNow();
            consumer.shutdownNow();
            producer.awaitTermination(10, TimeUnit.SECONDS);
            consumer.awaitTermination(10, TimeUnit.SECONDS);
        }
        Files.createDirectories(Path.of("results"));
        Files.write(Path.of("results/lab3-flowable-summary.txt"), report, StandardCharsets.UTF_8);
        report.forEach(System.out::println);
    }
}
