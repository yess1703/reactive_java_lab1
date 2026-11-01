package ru.music;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.reactivestreams.Subscription;

public final class ReactiveStatisticsTest {
    public static void main(String[] args) throws Exception {
        MusicGenerator generator = new MusicGenerator(42);
        var albums = generator.generateAlbums(500, generator.generateArtists(100));
        var tracks = generator.generateTracks(2500, albums);
        try (var rx = new ReactiveStatistics(8)) {
            for (int count : new int[]{0, 1, 500, 2500}) {
                var input = tracks.subList(0, count);
                var expected = StatisticsCalculator.iterative(input);
                for (long delay : new long[]{0, 1}) {
                    check(expected.equals(rx.observable(input, delay).blockingGet()), "Observable " + count);
                }
            }
            // Повторные подписки должны иметь независимые накопители.
            var calculation = rx.observable(tracks, 0);
            check(calculation.blockingGet().equals(calculation.blockingGet()), "Холодный Observable");
            try { rx.observable(tracks, -1); throw new AssertionError("Отрицательная задержка"); }
            catch (IllegalArgumentException expected) { }
        }
        // Доказываем реальную конкурентную обработку, а не только subscribeOn одной задачи.
        Set<String> threads = ConcurrentHashMap.newKeySet();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        List<Track> observed = new java.util.AbstractList<>() {
            public int size() { return 32; }
            public Track get(int index) {
                threads.add(Thread.currentThread().getName());
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                try { Thread.sleep(2); } catch (InterruptedException error) { throw new RuntimeException(error); }
                finally { active.decrementAndGet(); }
                return tracks.get(index);
            }
        };
        try (var rx = new ReactiveStatistics(4)) { rx.observable(observed, 0).blockingGet(); }
        check(threads.size() > 1 && maxActive.get() > 1, "Observable действительно многопоточный");

        var executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "test-generator"));
        try {
            var scheduler = Schedulers.from(executor);
            AtomicInteger generated = new AtomicInteger();
            var source = ReactiveTrackSource.generate(20, 42, scheduler)
                    .doOnNext(track -> generated.incrementAndGet());
            TestSubscriber<Track> probe = new TestSubscriber<>(0L);
            source.subscribe(probe);
            // Барьер на том же executor: подписка установлена, спроса ещё нет.
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            probe.assertNoValues();
            check(generated.get() == 0, "Нет генерации без request");
            probe.request(3);
            probe.awaitCount(3).assertValueCount(3).assertNotComplete();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            check(generated.get() == 3, "Создано ровно три запрошенных трека");
            probe.request(17);
            probe.awaitDone(5, TimeUnit.SECONDS).assertValueCount(20).assertComplete().assertNoErrors();

            AtomicInteger emitted = new AtomicInteger();
            TestSubscriber<Track> cancelled = new TestSubscriber<>(0L);
            ReactiveTrackSource.generate(1_000_000, 42, scheduler)
                    .doOnNext(t -> emitted.incrementAndGet()).subscribe(cancelled);
            cancelled.request(5);
            cancelled.awaitCount(5).assertValueCount(5);
            cancelled.cancel();
            executor.submit(() -> { }).get(5, TimeUnit.SECONDS);
            check(emitted.get() == 5, "Отмена останавливает источник");

            for (int count : new int[]{0, 1, 257, 2500}) {
                try (var subscriber = new StatisticsSubscriber(17)) {
                    ReactiveTrackSource.generate(count, 42, scheduler).subscribe(subscriber);
                    check(StatisticsCalculator.iterative(tracks.subList(0, count))
                            .equals(subscriber.await(Duration.ofSeconds(10))), "Flowable " + count);
                }
            }
            AtomicLong requests = new AtomicLong();
            try (var subscriber = new StatisticsSubscriber(17)) {
                Flowable.fromIterable(tracks).doOnRequest(n -> {
                    check(n == 17, "Subscriber запрашивает ограниченные порции");
                    requests.incrementAndGet();
                }).subscribe(subscriber);
                check(subscriber.await(Duration.ofSeconds(5)).trackCount() == 2500
                        && requests.get() > 1, "Пополнение спроса");
            }
            try (var subscriber = new StatisticsSubscriber(5)) {
                Flowable.<Track>error(new IllegalStateException("test error")).subscribe(subscriber);
                try { subscriber.await(Duration.ofSeconds(5)); throw new AssertionError("Ошибка потеряна"); }
                catch (java.util.concurrent.ExecutionException expected) {
                    check(expected.getCause() instanceof IllegalStateException, "Передача ошибки");
                }
            }
            try (var subscriber = new StatisticsSubscriber(5)) {
                AtomicInteger cancellations = new AtomicInteger();
                subscriber.onSubscribe(new Subscription() {
                    public void request(long n) { }
                    public void cancel() { cancellations.incrementAndGet(); }
                });
                try { subscriber.await(Duration.ofMillis(10)); throw new AssertionError("Нет timeout"); }
                catch (java.util.concurrent.TimeoutException expected) { }
                check(cancellations.get() == 1, "Timeout отменяет подписку");
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        System.out.println("ЛР3: Observable, конкурентность, Flowable, request, отмена и ошибки — OK");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
