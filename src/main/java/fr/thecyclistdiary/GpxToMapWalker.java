package fr.thecyclistdiary;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

public class GpxToMapWalker extends SimpleFileVisitor<Path> {
    public static final String GPX_EXTENSION = ".gpx";
    private final Set<String> modifiedGpxFiles;
    private final Set<Path> modifiedFolders = new HashSet<>();

    public GpxToMapWalker(Set<String> modifiedGpxFiles) {
        this.modifiedGpxFiles = modifiedGpxFiles;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        if (modifiedGpxFiles.contains(file.getFileName().toString())) {
            System.out.printf("The GPX file %s is new or as been modified during last commit, its parent will be updated%n", file);
            modifiedFolders.add(file.getParent());
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        updateDirWithSubDirsData(dir);
        System.out.printf("Done visiting directory %s%n", dir.getFileName());
        return super.postVisitDirectory(dir, exc);
    }

    private void updateDirWithSubDirsData(Path dir) throws IOException {
        try (Stream<Path> subFolders = Files.list(dir)) {
            List<Path> subFoldersList = subFolders.toList();
            Optional<Path> hasAnyModifiedSubfolder = subFoldersList.stream()
                    .filter(modifiedFolders::contains)
                    .findAny();
            if (hasAnyModifiedSubfolder.isPresent()) {
                System.out.printf("Some GPX files were update in the folder %s, the merged gpx will be regenerated%n", dir);
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(f -> f.getFileName().endsWith(GPX_EXTENSION))
                            .findAny().ifPresent(gpx -> {
                                try {
                                    System.out.printf("Deleting old gpx file : %s%n", gpx.getFileName());
                                    Files.delete(gpx);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
                List<File> collectChildGpxFiles = collectChildGpxFiles(subFoldersList);
                if (collectChildGpxFiles.isEmpty()) {
                    System.out.println("No GPX files could be collected in this folder's direct subfolders, skipping...");
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
        System.out.printf("%d child GPX files were collected%n", gpxFiles.size());
        return gpxFiles;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        return super.visitFileFailed(file, exc);
    }
}
