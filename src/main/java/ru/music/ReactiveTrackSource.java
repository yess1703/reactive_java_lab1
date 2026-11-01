package ru.music;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import java.util.List;
import java.util.Objects;

public final class ReactiveTrackSource {
    private ReactiveTrackSource() { }

    private static final class State {
        final MusicGenerator generator;
        final List<Album> albums;
        long emitted;

        State(long seed) {
            generator = new MusicGenerator(seed);
            albums = generator.generateAlbums(500, generator.generateArtists(100));
        }
    }

    public static Flowable<Track> generate(long count, long seed, Scheduler scheduler) {
        if (count < 0) throw new IllegalArgumentException("Отрицательное количество");
        Objects.requireNonNull(scheduler, "scheduler");
        // Новый State на каждую подписку. Один вызов генератора = один запрошенный элемент.
        return Flowable.<Track, State>generate(() -> new State(seed), (state, emitter) -> {
            if (state.emitted == count) {
                emitter.onComplete();
            } else {
                emitter.onNext(state.generator.nextTrack(state.albums));
                if (++state.emitted == count) emitter.onComplete();
            }
            return state;
        }).subscribeOn(scheduler);
    }
}
