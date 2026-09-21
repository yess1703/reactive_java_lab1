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
        print(report, "Музыкальный стриминговый сервис: сравнение трёх способов расчёта статистики.");
        print(report, "Версия Java: " + System.getProperty("java.version"));
        print(report, "Виртуальная машина: " + System.getProperty("java.vm.name"));
        print(report, "Операционная система: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        print(report, "Считаем количество треков, их длительность, прослушивания и распределение по жанрам.");
        print(report, "Измеряем только расчёт. Создание данных, проверки и вывод текста в замер не входят.");
        print(report, "Медиана - среднее по позиции время среди 7 замеров, отсортированных от быстрого к медленному.");
        print(report, "Минимум - самый быстрый замер. Чем меньше время, тем быстрее расчёт.");
        for (int size : new int[]{5_000, 50_000, 250_000}) {
            print(report, "");
            print(report, "Проверяем коллекцию из " + size + " треков.");
            print(report, "Шаг 1. Создаём 100 исполнителей, 500 альбомов и " + size + " случайных треков.");
            MusicGenerator generator = new MusicGenerator(42L);
            List<Artist> artists = generator.generateArtists(100);
            List<Album> albums = generator.generateAlbums(500, artists);
            List<Track> tracks = generator.generateTracks(size, albums);
            MusicStatistics expected = StatisticsCalculator.iterative(tracks);
            print(report, "Шаг 2. Прогреваем Java: выполняем каждый способ " + WARMUP_ROUNDS + " раз перед замерами.");
            for (int round = 0; round < WARMUP_ROUNDS; round++) {
                for (Method method : methods) {
                    sink = method.calculate().apply(tracks);
                    verify(expected, sink);
                }
            }
            print(report, "Шаг 3. Измеряем каждый способ " + MEASURED_ROUNDS + " раз и проверяем совпадение результатов.");
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
            print(report, "Шаг 4. Все результаты совпали. Время расчёта в миллисекундах:");
            print(report, String.format("%-24s | %12s | %12s", "Способ расчёта", "Медиана, мс", "Минимум, мс"));
            for (int index = 0; index < methods.size(); index++) {
                Arrays.sort(durations[index]);
                print(report, String.format(Locale.forLanguageTag("ru-RU"), "%-24s | %12.3f | %12.3f",
                        methods.get(index).name(), durations[index][MEASURED_ROUNDS / 2] / 1_000_000.0,
                        durations[index][0] / 1_000_000.0));
            }
            print(report, "Полученная статистика:");
            print(report, "  Всего треков: " + expected.trackCount());
            print(report, "  Общая длительность треков: " + expected.totalDurationSeconds() + " с");
            print(report, String.format(Locale.forLanguageTag("ru-RU"),
                    "  Средняя длительность одного трека: %.3f с", expected.averageDurationSeconds()));
            print(report, "  Общее число прослушиваний: " + expected.totalPlayCount());
            print(report, "  Количество треков по жанрам:");
            expected.tracksByGenre().forEach((genre, count) ->
                    print(report, "    " + genre.displayName() + ": " + count));
        }
        Path output = Path.of("results");
        Files.createDirectories(output);
        print(report, "");
        print(report, "Готово. Сводка: " + output.resolve("summary.txt").toAbsolutePath());
        print(report, "Все 63 замера с началом и концом: " + output.resolve("measurements.csv").toAbsolutePath());
        print(report, "В файле замеров время указано в наносекундах: 1 мс = 1 000 000 нс.");
        print(report, "Начало и конец — показания таймера System.nanoTime(), а не время суток. Длительность = конец − начало.");
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
