package com.jhz.movielens.hadoop.parse;

import com.jhz.movielens.hadoop.model.MovieRecord;
import com.jhz.movielens.hadoop.quality.IssueCode;
import com.jhz.movielens.hadoop.quality.QualityIssue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MovieParser implements MovieLensLineParser<MovieRecord> {
    private static final Set<String> ALLOWED_GENRES = Set.of(
            "Action", "Adventure", "Animation", "Children's", "Comedy", "Crime",
            "Documentary", "Drama", "Fantasy", "Film-Noir", "Horror", "Musical",
            "Mystery", "Romance", "Sci-Fi", "Thriller", "War", "Western");

    @Override
    public ParseResult<MovieRecord> parse(String line) {
        List<QualityIssue> issues = new ArrayList<>();
        String[] fields = ParserSupport.split(line, 3, issues);
        if (fields == null) {
            return new ParseResult<>(null, issues);
        }

        Integer movieId = ParserSupport.integer(fields[0], "movieId", issues);
        String title = ParserSupport.requiredText(fields[1], "title", issues);
        String rawGenres = ParserSupport.requiredText(fields[2], "genres", issues);
        if (movieId == null || title == null || rawGenres == null) {
            return new ParseResult<>(null, issues);
        }

        ParserSupport.requirePositiveIdentifier(movieId, "movieId", issues);
        List<String> genres = parseGenres(rawGenres, issues);
        return new ParseResult<>(new MovieRecord(movieId, title, genres), issues);
    }

    private List<String> parseGenres(String rawGenres, List<QualityIssue> issues) {
        String[] values = rawGenres.split("\\|", -1);
        List<String> genres = new ArrayList<>(values.length);
        Set<String> seen = new HashSet<>();

        for (String rawValue : values) {
            String genre = rawValue.trim();
            genres.add(genre);
            if (genre.isEmpty()) {
                issues.add(QualityIssue.error(
                        IssueCode.EMPTY_GENRE, "genres", "Genre value is empty."));
            } else if (!seen.add(genre)) {
                issues.add(QualityIssue.error(
                        IssueCode.DUPLICATE_GENRE, "genres", "Duplicate genre: " + genre));
            } else if (!ALLOWED_GENRES.contains(genre)) {
                issues.add(QualityIssue.warning(
                        IssueCode.UNKNOWN_GENRE, "genres", "Unknown MovieLens genre: " + genre));
            }
        }
        return genres;
    }
}
