package ru.music;

public record AudioFeatures(int tempoBpm, double energy, double danceability) {
    public AudioFeatures {
        if (tempoBpm < 30 || tempoBpm > 300) {
            throw new IllegalArgumentException("Темп должен быть от 30 до 300 ударов в минуту");
        }
        if (!Double.isFinite(energy) || energy < 0 || energy > 1
                || !Double.isFinite(danceability) || danceability < 0 || danceability > 1) {
            throw new IllegalArgumentException("Энергичность и танцевальность должны быть числами от 0 до 1");
        }
    }
}
