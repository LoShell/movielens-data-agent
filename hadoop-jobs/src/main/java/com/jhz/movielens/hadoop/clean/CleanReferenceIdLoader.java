package com.jhz.movielens.hadoop.clean;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

final class CleanReferenceIdLoader {
    private CleanReferenceIdLoader() {
    }

    static Set<Integer> load(Configuration configuration, Path directory) throws IOException {
        Set<Integer> ids = new HashSet<>();
        FileSystem fileSystem = directory.getFileSystem(configuration);
        RemoteIterator<LocatedFileStatus> files = fileSystem.listFiles(directory, true);
        while (files.hasNext()) {
            LocatedFileStatus status = files.next();
            String name = status.getPath().getName();
            if (name.startsWith("_") || name.startsWith(".")) {
                continue;
            }
            readIds(fileSystem, status.getPath(), ids);
        }
        return ids;
    }

    private static void readIds(FileSystem fileSystem, Path path, Set<Integer> ids) throws IOException {
        try (FSDataInputStream input = fileSystem.open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int separator = line.indexOf("::");
                if (separator > 0) {
                    try {
                        ids.add(Integer.parseInt(line.substring(0, separator)));
                    } catch (NumberFormatException exception) {
                        throw new IOException("Invalid identifier in cleaned reference file " + path + ": " + line, exception);
                    }
                }
            }
        }
    }
}
