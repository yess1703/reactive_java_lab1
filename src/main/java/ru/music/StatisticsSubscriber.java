package ru.music;

import io.reactivex.rxjava3.core.FlowableSubscriber;
import org.reactivestreams.Subscription;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Запрашивает следующую порцию только после обработки текущей. Треки не хранит. */
public final class StatisticsSubscriber implements FlowableSubscriber<Track>, AutoCloseable {
    private static final Subscription CANCELLED = new Subscription() {
        public void request(long n) { }
        public void cancel() { }
    };
    private final int batchSize;
    private final AtomicReference<Subscription> upstream = new AtomicReference<>();
    private final MusicStatisticsCollector.Accumulator accumulator = new MusicStatisticsCollector.Accumulator();
    private final CompletableFuture<MusicStatistics> result = new CompletableFuture<>();
    private int remaining;

    public StatisticsSubscriber(int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize должен быть положительным");
        this.batchSize = batchSize;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (!upstream.compareAndSet(null, subscription)) {
            subscription.cancel();
            return;
        }
        remaining = batchSize;
        subscription.request(batchSize);
    }

    @Override
    public void onNext(Track track) {
        if (result.isDone()) return;
        try {
            accumulator.add(track, 0); // Никакой искусственной задержки в третьем пункте.
            if (--remaining == 0) {
                remaining = batchSize;
                upstream.get().request(batchSize);
            }
        } catch (RuntimeException error) {
            cancelUpstream();
            onError(error);
        }
    }

    @Override
    public void onError(Throwable error) { result.completeExceptionally(error); }

    @Override
    public void onComplete() {
        if (!result.isDone()) result.complete(accumulator.finish());
    }

    public MusicStatistics await(Duration timeout) throws Exception {
        try {
            return result.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            close();
            Thread.currentThread().interrupt();
            throw error;
        } catch (java.util.concurrent.TimeoutException error) {
            close();
            throw error;
        }
    }

    private void cancelUpstream() {
        Subscription previous = upstream.getAndSet(CANCELLED);
        if (previous != null && previous != CANCELLED) previous.cancel();
    }

    @Override
    public void close() {
        cancelUpstream();
        result.cancel(false);
    }
}
