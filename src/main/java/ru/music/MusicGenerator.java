package ru.music;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class MusicGenerator {
    private static final String[] COUNTRIES = {"Russia", "France", "Brazil", "Japan", "Canada"};
    private static final String[] WORDS = {"Moon", "Ocean", "Echo", "Dream", "Sky", "Fire", "Light"};
    private static final List<String> TAGS = List.of("relax", "workout", "travel", "party", "focus");
    private final Random random;
    private long artistId;
    private long albumId;
    private long trackId;

    public MusicGenerator(long seed) {
        random = new Random(seed);
    }

    public List<Artist> generateArtists(int count) {
        checkCount(count);
        List<Artist> artists = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long id = ++artistId;
            artists.add(new Artist(id, name("Artist", id), COUNTRIES[random.nextInt(COUNTRIES.length)]));
        }
        return List.copyOf(artists);
    }

    public List<Album> generateAlbums(int count, List<Artist> artists) {
        checkCount(count);
        List<Artist> pool = List.copyOf(artists);
        if (count > 0 && pool.isEmpty()) {
            throw new IllegalArgumentException("Чтобы создать альбомы, сначала создайте хотя бы одного исполнителя");
        }
        List<Album> albums = new ArrayList<>(count);
        LocalDate firstDate = LocalDate.of(1980, 1, 1);
        long days = LocalDate.now().toEpochDay() - firstDate.toEpochDay() + 1;
        for (int i = 0; i < count; i++) {
            long id = ++albumId;
            albums.add(new Album(id, name("Album", id), firstDate.plusDays(random.nextLong(days)),
                    pool.get(random.nextInt(pool.size()))));
        }
        return List.copyOf(albums);
    }

    public List<Track> generateTracks(int count, List<Album> albums) {
        checkCount(count);
        List<Album> pool = List.copyOf(albums);
        if (count > 0 && pool.isEmpty()) {
            throw new IllegalArgumentException("Чтобы создать треки, сначала создайте хотя бы один альбом");
        }
        List<Track> tracks = new ArrayList<>(count);
        Genre[] genres = Genre.values();
        for (int i = 0; i < count; i++) {
            long id = ++trackId;
            Album album = pool.get(random.nextInt(pool.size()));
            int tagIndex = random.nextInt(TAGS.size());
            List<String> tags = random.nextBoolean() ? List.of(TAGS.get(tagIndex))
                    : List.of(TAGS.get(tagIndex), TAGS.get((tagIndex + 1) % TAGS.size()));
            tracks.add(new Track(id, name("Track", id), random.nextInt(30, 901),
                    random.nextLong(100_000_001L), album.releaseDate(),
                    genres[random.nextInt(genres.length)],
                    new AudioFeatures(random.nextInt(40, 221), random.nextDouble(), random.nextDouble()),
                    tags, album));
        }
        return List.copyOf(tracks);
    }

    private String name(String prefix, long id) {
        return prefix + " " + WORDS[random.nextInt(WORDS.length)] + " " + id;
    }

    private static void checkCount(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Количество создаваемых объектов не может быть отрицательным");
        }
    }
}
