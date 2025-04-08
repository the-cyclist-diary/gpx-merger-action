package fr.thecyclistdiary;

import io.quarkus.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class GpxToMapWalker extends SimpleFileVisitor<Path> {
    public static final String GPX_EXTENSION = ".gpx";
    private final Set<String> modifiedGpxFiles;

    public GpxToMapWalker(Set<String> modifiedGpxFiles) {
        this.modifiedGpxFiles = modifiedGpxFiles;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Log.info("Done visiting directory %s".formatted(dir.getFileName()));
        updateDirWithSubDirsData(dir);
        return super.postVisitDirectory(dir, exc);
    }

    private void updateDirWithSubDirsData(Path dir) throws IOException {
        try (Stream<Path> subFolders = Files.list(dir)) {
            List<Path> subFoldersList = subFolders.toList();
            Optional<Path> hasAnyModifiedSubfolder = subFoldersList.stream()
                    .filter(sf -> modifiedGpxFiles.contains(sf.toString()))
                    .findAny();
            if (hasAnyModifiedSubfolder.isPresent()) {
                Log.info("Some GPX files were update in the folder %s, the merged gpx will be regenerated".formatted(dir));
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(f -> f.getFileName().endsWith(GPX_EXTENSION))
                            .findAny().ifPresent(gpx -> {
                                try {
                                    Log.info("Deleting old gpx file : %s".formatted(gpx.getFileName()));
                                    Files.delete(gpx);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
                List<File> collectChildGpxFiles = collectChildGpxFiles(subFoldersList);
                if (collectChildGpxFiles.isEmpty()) {
                    Log.info("No GPX files could be collected in this folder's direct subfolders, skipping...");
                } else {
                    GpxParser.concatGpxFiles(collectChildGpxFiles.stream(), dir);
                }
            }
        }
    }

    private List<File> collectChildGpxFiles(List<Path> subFoldersList) {
        List<File> gpxFiles = new ArrayList<>();
        subFoldersList.stream()
                .filter(Files::isDirectory)
                .forEach(subFolder -> {
                    try (Stream<Path> files = Files.list(subFolder)) {
                        files.filter(f -> f.getFileName().toString().endsWith(GPX_EXTENSION))
                                .findAny()
                                .ifPresent(gpxFile -> gpxFiles.add(gpxFile.toFile()));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        Log.info("%d child GPX files were collected".formatted(gpxFiles.size()));
        return gpxFiles;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return super.visitFileFailed(file, exc);
    }
}
