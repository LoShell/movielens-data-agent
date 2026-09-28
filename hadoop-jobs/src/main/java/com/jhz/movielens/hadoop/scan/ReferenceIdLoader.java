package com.jhz.movielens.hadoop.scan;

import com.jhz.movielens.hadoop.model.MovieRecord;
import com.jhz.movielens.hadoop.model.UserRecord;
import com.jhz.movielens.hadoop.parse.MovieLensLineParser;
import com.jhz.movielens.hadoop.parse.MovieParser;
import com.jhz.movielens.hadoop.parse.ParseResult;
import com.jhz.movielens.hadoop.parse.UserParser;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.function.ToIntFunction;

final class ReferenceIdLoader {
    private ReferenceIdLoader() {
    }

    static Set<Integer> loadUserIds(Configuration configuration, Path path) throws IOException {
        return loadIds(configuration, path, new UserParser(), UserRecord::userId);
    }

    static Set<Integer> loadMovieIds(Configuration configuration, Path path) throws IOException {
        return loadIds(configuration, path, new MovieParser(), MovieRecord::movieId);
    }

    private static <T> Set<Integer> loadIds(
            Configuration configuration,
            Path path,
            MovieLensLineParser<T> parser,
            ToIntFunction<T> idExtractor) throws IOException {
        Set<Integer> ids = new HashSet<>();
        FileSystem fileSystem = path.getFileSystem(configuration);
        try (FSDataInputStream input = fileSystem.open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                ParseResult<T> result = parser.parse(line);
                result.parsedValue().ifPresent(value -> {
                    int id = idExtractor.applyAsInt(value);
                    if (id > 0) {
                        ids.add(id);
                    }
                });
            }
        }
        return ids;
    }
}
