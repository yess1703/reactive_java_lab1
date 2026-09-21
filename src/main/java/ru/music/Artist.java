package ru.music;

import java.util.Objects;

public final class Artist {
    private final long id;
    private final String name;
    private final String country;

    public Artist(long id, String name, String country) {
        if (id <= 0 || Objects.requireNonNull(name).isBlank()
                || Objects.requireNonNull(country).isBlank()) {
            throw new IllegalArgumentException("У исполнителя должны быть положительный идентификатор, непустые имя и страна");
        }
        this.id = id;
        this.name = name;
        this.country = country;
    }

    public long id() { return id; }
    public String name() { return name; }
    public String country() { return country; }
}
