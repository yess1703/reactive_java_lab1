package ru.music;

import java.time.LocalDate;
import java.util.Objects;

public final class Album {
    private final long id;
    private final String title;
    private final LocalDate releaseDate;
    private final Artist artist;

    public Album(long id, String title, LocalDate releaseDate, Artist artist) {
        if (id <= 0 || Objects.requireNonNull(title).isBlank()) {
            throw new IllegalArgumentException("У альбома должны быть положительный идентификатор и непустое название");
        }
        this.releaseDate = Objects.requireNonNull(releaseDate);
        if (releaseDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Дата выпуска не может быть в будущем");
        }
        this.id = id;
        this.title = title;
        this.artist = Objects.requireNonNull(artist);
    }

    public long id() { return id; }
    public String title() { return title; }
    public LocalDate releaseDate() { return releaseDate; }
    public Artist artist() { return artist; }
}
