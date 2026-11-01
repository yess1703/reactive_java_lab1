package ru.music.benchmark;

import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import ru.music.ReactiveStatistics;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

public final class Lab3BenchmarkMain {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("results"));
        var results = new Runner(new OptionsBuilder()
                .include("^ru\\.music\\.benchmark\\.ReactiveStatisticsBenchmark\\..*$")
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result("results/lab3-jmh.json")
                .build()).run();
        List<String> report = new ArrayList<>();
        report.add("ЛР3: JMH 1.37, среднее ± погрешность, мс/полный сбор, задержка 1 мс на трек.");
        report.add("Java: " + System.getProperty("java.version") + "; ОС: " + System.getProperty("os.name"));
        report.add("Доступных процессоров: " + Runtime.getRuntime().availableProcessors()
                + "; commonPool parallelism: " + ForkJoinPool.getCommonPoolParallelism()
                + "; Observable workers: " + ReactiveStatistics.defaultWorkers());
        report.add("2 forks; прогрев 3 x 500 мс; измерения 5 x 1 с; JMH threads = 1.");
        report.add("Observable использует больше потоков для блокирующего ожидания; это сравнение конфигураций, а не равных пулов.");
        report.add("Треков | parallel, мс/op | Observable, мс/op | ускорение parallel/Observable");
        Map<Integer, Map<String, RunResult>> samples = new TreeMap<>();
        for (var result : results) {
            String name = result.getParams().getBenchmark();
            samples.computeIfAbsent(Integer.parseInt(result.getParams().getParam("size")), k -> new HashMap<>())
                    .put(name.substring(name.lastIndexOf('.') + 1), result);
        }
        boolean passed = true;
        for (var entry : samples.entrySet()) {
            var parallel = entry.getValue().get("parallel").getPrimaryResult();
            var observable = entry.getValue().get("observable").getPrimaryResult();
            double speedup = parallel.getScore() / observable.getScore();
            passed &= speedup >= 1;
            report.add(String.format(Locale.US, "%d | %.3f ± %.3f | %.3f ± %.3f | %.3f",
                    entry.getKey(), parallel.getScore(), parallel.getScoreError(),
                    observable.getScore(), observable.getScoreError(), speedup));
        }
        report.add(passed ? "На обоих размерах Observable не медленнее по средним JMH: OK."
                : "Требование по среднему времени не достигнуто на одном из размеров.");
        Files.write(Path.of("results/lab3-summary.txt"), report, StandardCharsets.UTF_8);
        report.forEach(System.out::println);
        if (!passed) throw new IllegalStateException("Observable медленнее: см. результаты JMH");
    }
}
