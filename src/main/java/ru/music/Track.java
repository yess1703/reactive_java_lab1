package ru.music;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public final class Track {
    private final long id;
    private final String title;
    private final int durationSeconds;
    private final long playCount;
    private final LocalDate releaseDate;
    private final Genre genre;
    private final AudioFeatures audioFeatures;
    private final List<String> tags;
    private final Album album;

    public Track(long id, String title, int durationSeconds, long playCount,
                 LocalDate releaseDate, Genre genre, AudioFeatures audioFeatures,
                 List<String> tags, Album album) {
        if (id <= 0 || Objects.requireNonNull(title).isBlank() || durationSeconds <= 0 || playCount < 0) {
            throw new IllegalArgumentException("У трека должны быть положительные идентификатор и длительность, непустое название и неотрицательное число прослушиваний");
        }
        this.album = Objects.requireNonNull(album);
        this.releaseDate = Objects.requireNonNull(releaseDate);
        if (releaseDate.isAfter(album.releaseDate())) {
            throw new IllegalArgumentException("Дата выпуска трека не может быть позже даты выпуска альбома");
        }
        this.tags = List.copyOf(tags);
        if (this.tags.isEmpty() || this.tags.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("У трека должен быть хотя бы один тег; пустые теги недопустимы");
        }
        this.id = id;
        this.title = title;
        this.durationSeconds = durationSeconds;
        this.playCount = playCount;
        this.genre = Objects.requireNonNull(genre);
        this.audioFeatures = Objects.requireNonNull(audioFeatures);
    }

    public long id() { return id; }
    public String title() { return title; }
    public int durationSeconds() { return durationSeconds; }
    public long playCount() { return playCount; }
    public LocalDate releaseDate() { return releaseDate; }
    public Genre genre() { return genre; }
    public AudioFeatures audioFeatures() { return audioFeatures; }
    public List<String> tags() { return tags; }
    public Album album() { return album; }
    public Artist artist() { return album.artist(); }
}
