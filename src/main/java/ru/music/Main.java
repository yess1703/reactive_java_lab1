package ru.music;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public final class Main {
    private static final int WARMUP_ROUNDS = 10;
    private static final int MEASURED_ROUNDS = 7;
    private static volatile MusicStatistics sink;

    private record Method(String name, Function<List<Track>, MusicStatistics> calculate) { }

    public static void main(String[] args) throws IOException {
        List<Method> methods = List.of(
                new Method("Цикл for", StatisticsCalculator::iterative),
                new Method("Стандартные коллекторы", StatisticsCalculator::standardCollectors),
                new Method("Собственный коллектор", StatisticsCalculator::customCollector));
        List<String> measurements = new ArrayList<>();
        measurements.add("Количество треков,Способ расчёта,Номер повтора,Начало (нс),Конец (нс),Длительность (нс)");
        List<String> report = new ArrayList<>();
        // Сравниваем число треков, длительность, прослушивания и жанры
        // Медиана-четвёртое из семи времён после сортировки от быстрого к медленному
        // Минимум-самый быстрый замер
        for (int size : new int[]{5_000, 50_000, 250_000}) {
            if (!report.isEmpty()) {
                print(report, "");
            }
            // 100 исполнителей, 500 альбомов и size случайных треков
            MusicGenerator generator = new MusicGenerator(42L);
            List<Artist> artists = generator.generateArtists(100);
            List<Album> albums = generator.generateAlbums(500, artists);
            List<Track> tracks = generator.generateTracks(size, albums);
            MusicStatistics expected = StatisticsCalculator.iterative(tracks);
            // Прогрев
            for (int round = 0; round < WARMUP_ROUNDS; round++) {
                for (Method method : methods) {
                    sink = method.calculate().apply(tracks);
                    verify(expected, sink);
                }
            }
            // Семь замеров на способ; результаты сверяются с расчётом циклом; при расхождении программа останавливается
            long[][] durations = new long[methods.size()][MEASURED_ROUNDS];
            for (int round = 0; round < MEASURED_ROUNDS; round++) {
                for (int offset = 0; offset < methods.size(); offset++) {
                    int index = (round + offset) % methods.size();
                    Method method = methods.get(index);
                    long start = System.nanoTime();
                    MusicStatistics result = method.calculate().apply(tracks);
                    long end = System.nanoTime();
                    sink = result;
                    verify(expected, result);
                    durations[index][round] = end - start;
                    measurements.add(size + "," + method.name() + "," + (round + 1) + ","
                            + start + "," + end + "," + (end - start));
                }
            }
            print(report, String.format("%-24s | %12s | %12s", "Способ расчёта", "Медиана, мс", "Минимум, мс"));
            for (int index = 0; index < methods.size(); index++) {
                Arrays.sort(durations[index]);
                print(report, String.format(Locale.forLanguageTag("ru-RU"), "%-24s | %12.3f | %12.3f",
                        methods.get(index).name(), durations[index][MEASURED_ROUNDS / 2] / 1_000_000.0,
                        durations[index][0] / 1_000_000.0));
            }
            print(report, "Всего треков: " + expected.trackCount());
            print(report, "Общая длительность треков: " + expected.totalDurationSeconds() + " с");
            print(report, String.format(Locale.forLanguageTag("ru-RU"),
                    "Средняя длительность одного трека: %.3f с", expected.averageDurationSeconds()));
            print(report, "Общее число прослушиваний: " + expected.totalPlayCount());
            print(report, "Количество треков по жанрам:");
            expected.tracksByGenre().forEach((genre, count) ->
                    print(report, "  " + genre.displayName() + ": " + count));
        }
        Path output = Path.of("results");
        Files.createDirectories(output);
        Files.writeString(output.resolve("measurements.csv"), "\uFEFF" + String.join(System.lineSeparator(), measurements)
                + System.lineSeparator(), StandardCharsets.UTF_8);
        Files.write(output.resolve("summary.txt"), report, StandardCharsets.UTF_8);
    }

    private static void print(List<String> report, String message) {
        report.add(message);
        System.out.println(message);
    }

    private static void verify(MusicStatistics expected, MusicStatistics actual) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Результаты способов расчёта не совпали. Ожидалось: "
                    + expected + "; получено: " + actual);
        }
    }
}
