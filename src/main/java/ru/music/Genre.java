package ru.music;

public enum Genre {
    POP("Поп"), ROCK("Рок"), JAZZ("Джаз"), CLASSICAL("Классика"),
    ELECTRONIC("Электронная музыка"), HIP_HOP("Хип-хоп");

    private final String displayName;

    Genre(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
