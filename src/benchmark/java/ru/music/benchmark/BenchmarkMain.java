package ru.music.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class BenchmarkMain {
    private static final Locale RU = Locale.forLanguageTag("ru-RU");

    private record Sample(int size, double sequential, double sequentialError, double parallel,
                          double parallelError, double forkJoin, double forkJoinError, String unit) { }

    public static void main(String[] args) throws Exception {
        List<String> report = new ArrayList<>();
        List<String> csv = new ArrayList<>();
        csv.add("Задержка (мс),Количество треков,Способ,Среднее время,Погрешность,Единица");
        // В отчёт попадают только числа. Условия замера: JMH AverageTime, 2 прогрева по 200 мс,
        // 3 измерения по 400 мс, отдельная JVM на каждую пару «размер × способ».
        // Данные: сид 42, 100 исполнителей, 500 альбомов.
        // Задержка — Thread.sleep в Artist.country(long) при запросе страны исполнителя.
        // parallelStream считает в общем пуле (availableProcessors - 1).
        // Свой ForkJoinPool создаётся на availableProcessors: список делится пополам до порога,
        // фрагмент считается последовательным накопителем.
        // Параллельный коллектор один на все потоки: LongAdder, ConcurrentHashMap для жанров,
        // ConcurrentSkipListMap для стран.
        measureSuite(report, csv, 0, List.of(1_000, 5_000, 15_000, 50_000, 100_000, 250_000), 1_000_000);
        measureSuite(report, csv, 1, List.of(1, 2, 4, 8, 16, 32, 64, 128), 512);
        Path output = Path.of("results");
        Files.createDirectories(output);
        Files.writeString(output.resolve("lab2-measurements.csv"),
                "\uFEFF" + String.join(System.lineSeparator(), csv) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        Files.write(output.resolve("lab2-summary.txt"), report, StandardCharsets.UTF_8);
        System.out.println();
        report.forEach(System.out::println);
    }

    private static void measureSuite(List<String> report, List<String> csv, long delayMillis,
                                     List<Integer> initialSizes, int maxSize) throws Exception {
        List<Integer> sizes = new ArrayList<>(initialSizes);
        Map<Integer, Sample> samples = new TreeMap<>();
        for (int attempt = 0; attempt < 4 && !hasCrossover(samples); attempt++) {
            List<Integer> missing = sizes.stream().filter(size -> !samples.containsKey(size)).toList();
            if (missing.isEmpty()) {
                break;
            }
            for (Sample sample : measure(delayMillis, missing)) {
                samples.put(sample.size(), sample);
            }
            if (hasCrossover(samples) || !extend(sizes, samples, maxSize)) {
                break;
            }
        }
        writeSuite(report, csv, delayMillis, new ArrayList<>(samples.values()));
    }

    private static boolean extend(List<Integer> sizes, Map<Integer, Sample> samples, int maxSize) {
        if (samples.size() < 2) {
            return false;
        }
        List<Sample> sorted = samples.values().stream().sorted(Comparator.comparingInt(Sample::size)).toList();
        Sample first = sorted.get(0);
        Sample last = sorted.get(sorted.size() - 1);
        double firstGap = first.parallel() - first.sequential();
        double lastGap = last.parallel() - last.sequential();
        if (firstGap < 0 && lastGap < 0) {
            int smaller = Math.max(1, first.size() / 2);
            if (smaller == first.size() || sizes.contains(smaller)) {
                return false;
            }
            sizes.add(0, smaller);
            return true;
        }
        if (firstGap > 0 && lastGap > 0) {
            Sample previous = sorted.get(sorted.size() - 2);
            double previousRatio = ratio(previous);
            double lastRatio = ratio(last);
            int bigger = last.size() * 2;
            if (bigger > maxSize || lastRatio >= previousRatio) {
                return false;
            }
            sizes.add(bigger);
            return true;
        }
        return false;
    }

    private static List<Sample> measure(long delayMillis, List<Integer> sizes) throws Exception {
        String[] sizeParams = sizes.stream().map(String::valueOf).toArray(String[]::new);
        Options options = new OptionsBuilder()
                .include(Pattern.quote("ru.music.benchmark.StatisticsBenchmark."))
                .param("delayMillis", Long.toString(delayMillis))
                .param("size", sizeParams)
                .jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
                .shouldFailOnError(true)
                .build();
        Collection<RunResult> results = new Runner(options).run();
        Map<Integer, MutableSample> grouped = new TreeMap<>();
        for (RunResult result : results) {
            String benchmark = result.getParams().getBenchmark();
            String method = benchmark.substring(benchmark.lastIndexOf('.') + 1);
            int size = Integer.parseInt(result.getParams().getParam("size"));
            MutableSample sample = grouped.computeIfAbsent(size, MutableSample::new);
            double score = result.getPrimaryResult().getScore();
            double error = result.getPrimaryResult().getScoreError();
            if (Double.isNaN(error)) {
                error = 0;
            }
            sample.unit = result.getPrimaryResult().getScoreUnit();
            switch (method) {
                case "sequential" -> {
                    sample.sequential = score;
                    sample.sequentialError = error;
                }
                case "parallel" -> {
                    sample.parallel = score;
                    sample.parallelError = error;
                }
                case "forkJoin" -> {
                    sample.forkJoin = score;
                    sample.forkJoinError = error;
                }
                default -> throw new IllegalStateException("Неизвестный замер: " + benchmark);
            }
        }
        List<Sample> samples = new ArrayList<>();
        for (MutableSample sample : grouped.values()) {
            if (sample.sequential == null || sample.parallel == null || sample.forkJoin == null) {
                throw new IllegalStateException("Неполный набор замеров для " + sample.size + " треков");
            }
            samples.add(new Sample(sample.size, sample.sequential, sample.sequentialError, sample.parallel,
                    sample.parallelError, sample.forkJoin, sample.forkJoinError, sample.unit));
        }
        return samples;
    }

    private static void writeSuite(List<String> report, List<String> csv, long delayMillis, List<Sample> samples) {
        if (samples.isEmpty()) {
            throw new IllegalStateException("JMH не вернул ни одного замера");
        }
        samples.sort(Comparator.comparingInt(Sample::size));
        String unit = samples.get(0).unit();
        if (!report.isEmpty()) {
            report.add("");
        }
        report.add("Задержка, мс: " + delayMillis);
        // Ускорение = время последовательного стрима / время параллельного.
        // В ячейках — среднее JMH и погрешность того же прогона.
        report.add(String.format("%8s | %24s | %24s | %24s | %12s",
                "Треков", "Последовательный, " + unit, "Параллельный, " + unit, "ForkJoinPool, " + unit, "Ускорение"));
        for (Sample sample : samples) {
            report.add(String.format(RU, "%8d | %14.3f ± %-6.3f | %14.3f ± %-6.3f | %14.3f ± %-6.3f | %12.3f",
                    sample.size(), sample.sequential(), sample.sequentialError(), sample.parallel(),
                    sample.parallelError(), sample.forkJoin(), sample.forkJoinError(),
                    sample.sequential() / sample.parallel()));
            csv.add(row(delayMillis, sample.size(), "Последовательный стрим", sample.sequential(),
                    sample.sequentialError(), sample.unit()));
            csv.add(row(delayMillis, sample.size(), "Параллельный стрим", sample.parallel(),
                    sample.parallelError(), sample.unit()));
            csv.add(row(delayMillis, sample.size(), "Свой ForkJoinPool", sample.forkJoin(),
                    sample.forkJoinError(), sample.unit()));
        }
        report.addAll(crossoverLines(samples));
    }

    private static String row(long delayMillis, int size, String method, double score, double error, String unit) {
        return delayMillis + "," + size + "," + method + ","
                + String.format(Locale.US, "%.6f", score) + ","
                + String.format(Locale.US, "%.6f", error) + "," + unit;
    }

    // Равная скорость — число треков, где средние времена последовательного и параллельного стримов совпадают.
    // Если между соседними размерами более быстрый способ меняется, число получается линейной интерполяцией.
    // Если способ не меняется, но относительная разница средних не больше 5%, берётся ближайший измеренный размер.
    // Пересечение погрешностей — размеры, где интервалы JMH пересекаются и разница средних не больше 15%.
    // Порог 15% отсекает широкие интервалы трёх итераций: иначе в список попадают явно разные замеры.
    private static List<String> crossoverLines(List<Sample> samples) {
        List<String> lines = new ArrayList<>();
        Sample closest = samples.stream().min(Comparator.comparingDouble(BenchmarkMain::ratio)).orElseThrow();
        Integer interpolated = interpolate(samples);
        if (interpolated != null) {
            lines.add("Равная скорость, треков: " + interpolated);
        } else if (ratio(closest) <= 0.05) {
            lines.add("Равная скорость, треков: " + closest.size());
            lines.add(String.format(RU, "Относительная разница, %%: %.1f", ratio(closest) * 100));
        } else {
            lines.add("Равная скорость, треков: —");
            lines.add("Ближайший размер: " + closest.size());
            lines.add(String.format(RU, "Относительная разница, %%: %.1f", ratio(closest) * 100));
        }
        List<Sample> equalWithinError = samples.stream()
                .filter(sample -> intervalsOverlap(sample) && ratio(sample) <= 0.15)
                .toList();
        if (!equalWithinError.isEmpty()) {
            lines.add("Пересечение погрешностей, треков: " + equalWithinError.stream()
                    .map(sample -> Integer.toString(sample.size()))
                    .reduce((left, right) -> left + ", " + right).orElseThrow());
        }
        return lines;
    }

    private static boolean hasCrossover(Map<Integer, Sample> samples) {
        return samples.size() >= 2 && interpolate(new ArrayList<>(samples.values())) != null;
    }

    private static Integer interpolate(List<Sample> samples) {
        List<Sample> sorted = samples.stream().sorted(Comparator.comparingInt(Sample::size)).toList();
        for (Sample sample : sorted) {
            if (sample.sequential() == sample.parallel()) {
                return sample.size();
            }
        }
        for (int index = 1; index < sorted.size(); index++) {
            Sample left = sorted.get(index - 1);
            Sample right = sorted.get(index);
            double leftGap = left.sequential() - left.parallel();
            double rightGap = right.sequential() - right.parallel();
            if ((leftGap < 0 && rightGap > 0) || (leftGap > 0 && rightGap < 0)) {
                double position = left.size() + (right.size() - left.size()) * (-leftGap) / (rightGap - leftGap);
                return (int) Math.round(position);
            }
        }
        return null;
    }

    private static boolean intervalsOverlap(Sample sample) {
        double sequentialLow = sample.sequential() - sample.sequentialError();
        double sequentialHigh = sample.sequential() + sample.sequentialError();
        double parallelLow = sample.parallel() - sample.parallelError();
        double parallelHigh = sample.parallel() + sample.parallelError();
        return sequentialLow <= parallelHigh && parallelLow <= sequentialHigh;
    }

    private static double ratio(Sample sample) {
        double base = Math.max(sample.sequential(), sample.parallel());
        if (base == 0) {
            return 0;
        }
        return Math.abs(sample.sequential() - sample.parallel()) / base;
    }

    private static final class MutableSample {
        private final int size;
        private Double sequential;
        private double sequentialError;
        private Double parallel;
        private double parallelError;
        private Double forkJoin;
        private double forkJoinError;
        private String unit = "";

        private MutableSample(int size) {
            this.size = size;
        }
    }
}
