package ru.music;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class StatisticsTest {
    public static void main(String[] args) {
        System.out.println("Проверка 1 из 4. Сравниваем расчёты с известным ответом: 3 трека, 600 секунд, 60 прослушиваний.");
        Artist artist = new Artist(1, "Artist", "Japan");
        Album album = new Album(1, "Album", LocalDate.of(2020, 1, 1), artist);
        AudioFeatures features = new AudioFeatures(120, 0.5, 0.7);
        List<Track> fixture = List.of(
                new Track(1, "One", 120, 10, album.releaseDate(), Genre.ROCK, features, List.of("focus"), album),
                new Track(2, "Two", 180, 20, album.releaseDate(), Genre.JAZZ, features, List.of("relax"), album),
                new Track(3, "Three", 300, 30, album.releaseDate(), Genre.ROCK, features, List.of("travel"), album));
        MusicStatistics expected = new MusicStatistics(3, 600, 60, Map.of(Genre.ROCK, 2L, Genre.JAZZ, 1L));
        verifyAll(fixture, expected);
        check(expected.averageDurationSeconds() == 200.0, "Средняя длительность трека");
        System.out.println("Проверка 2 из 4. Проверяем пустую коллекцию и коллекцию из одного трека.");
        verifyAll(List.of(), new MusicStatistics(0, 0, 0, Map.of()));
        check(StatisticsCalculator.iterative(List.of()).averageDurationSeconds() == 0.0, "Средняя длительность для пустой коллекции");
        verifyAll(List.of(fixture.get(0)), new MusicStatistics(1, 120, 10, Map.of(Genre.ROCK, 1L)));

        System.out.println("Проверка 3 из 4. Создаём данные, проверяем их количество, уникальность, связи и расчёт на 5000 треках.");
        MusicGenerator generator = new MusicGenerator(42);
        List<Artist> artists = generator.generateArtists(100);
        List<Album> albums = generator.generateAlbums(500, artists);
        List<Track> tracks = generator.generateTracks(5_000, albums);
        check(artists.size() == 100 && albums.size() == 500 && tracks.size() == 5_000, "Количество созданных исполнителей, альбомов и треков");
        check(artists.stream().map(Artist::id).distinct().count() == 100, "Уникальность идентификаторов исполнителей");
        check(albums.stream().map(Album::id).distinct().count() == 500, "Уникальность идентификаторов альбомов");
        check(tracks.stream().map(Track::id).distinct().count() == 5_000, "Уникальность идентификаторов треков");
        check(generator.generateTracks(1, albums).get(0).id() == 5_001, "Уникальность идентификаторов при повторной генерации");
        check(generator.generateArtists(0).isEmpty(), "Создание нуля исполнителей");
        check(generator.generateAlbums(0, List.of()).isEmpty(), "Создание нуля альбомов");
        check(generator.generateTracks(0, List.of()).isEmpty(), "Создание нуля треков");
        var artistPool = new HashSet<>(artists);
        var albumPool = new HashSet<>(albums);
        check(albums.stream().allMatch(a -> artistPool.contains(a.artist())), "Связь альбомов с созданными исполнителями");
        check(tracks.stream().allMatch(t -> albumPool.contains(t.album())
                && t.durationSeconds() >= 30 && t.durationSeconds() <= 900
                && t.playCount() >= 0 && t.playCount() <= 100_000_000
                && !t.releaseDate().isAfter(LocalDate.now())
                && t.releaseDate().equals(t.album().releaseDate())
                && !t.tags().isEmpty()), "Допустимость характеристик и связей созданных треков");
        verifyAll(tracks, StatisticsCalculator.iterative(tracks));
        System.out.println("Проверка 4 из 4. Убеждаемся, что неверные данные и изменение защищённых коллекций вызывают ошибки.");
        expectFailure(() -> generator.generateArtists(-1), IllegalArgumentException.class);
        expectFailure(() -> generator.generateAlbums(1, List.of()), IllegalArgumentException.class);
        expectFailure(() -> generator.generateTracks(1, List.of()), IllegalArgumentException.class);
        expectFailure(() -> new AudioFeatures(120, Double.NaN, 0.5), IllegalArgumentException.class);
        expectFailure(() -> new AudioFeatures(0, 0.5, 0.5), IllegalArgumentException.class);
        expectFailure(() -> new Track(1, "Bad", 0, 1, album.releaseDate(), Genre.POP,
                features, List.of("tag"), album), IllegalArgumentException.class);
        expectFailure(() -> fixture.get(0).tags().add("new"), UnsupportedOperationException.class);
        expectFailure(() -> expected.tracksByGenre().put(Genre.POP, 1L), UnsupportedOperationException.class);
        System.out.println("Все проверки пройдены: расчёты верны, генератор создаёт допустимые данные, ошибочные значения отклоняются, коллекции защищены от изменений, параллельный коллектор работает.");
    }

    private static void verifyAll(List<Track> tracks, MusicStatistics expected) {
        check(expected.equals(StatisticsCalculator.iterative(tracks)), "Результат расчёта циклом");
        check(expected.equals(StatisticsCalculator.standardCollectors(tracks)), "Результат стандартных коллекторов");
        check(expected.equals(StatisticsCalculator.customCollector(tracks)), "Результат собственного коллектора");
        check(expected.equals(tracks.parallelStream().collect(new MusicStatisticsCollector())), "Результат собственного коллектора в параллельном потоке");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Не пройдена проверка: " + message);
        }
    }

    private static void expectFailure(Runnable action, Class<? extends RuntimeException> expected) {
        try {
            action.run();
        } catch (RuntimeException error) {
            if (expected.isInstance(error)) {
                return;
            }
            throw error;
        }
        throw new AssertionError("Ожидалось исключение: " + expected.getSimpleName());
    }
}
