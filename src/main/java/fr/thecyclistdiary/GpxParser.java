package fr.thecyclistdiary;

import io.jenetics.jpx.GPX;
import io.jenetics.jpx.Track;
import io.jenetics.jpx.TrackSegment;
import io.jenetics.jpx.WayPoint;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Utility class for parsing and manipulating GPX files.
 */
public class GpxParser {

    public static final String INDENT = "    ";

    /**
     * Parse a list of {@link Track} from a GPX file
     *
     * @param gpxFile the GPX file to read
     * @return a list of {@link Track} parsed from the GPX file
     * @throws IOException if the GPX file cannot be read
     */
    public static List<Track> getTracks(File gpxFile) throws IOException {
        return GPX.read(Path.of(gpxFile.toURI())).tracks()
                .toList();
    }

    /**
     * Read {@link WayPoint} from a {@link Track}
     *
     * @param tracks list of {@link Track} to parse
     * @return list of parsed {@link WayPoint}
     */
    public static List<WayPoint> getWayPoints(List<Track> tracks) {
        return tracks.stream()
                .flatMap(Track::segments)
                .flatMap(TrackSegment::points)
                .toList();
    }

    /**
     * Concatenate multiple GPX files. Their {@link Track} will be sorted by the date of their most recent {@link WayPoint}.
     * This ensures that the GPX path will be coherent. However, if tracks are far apart from each other, strait lines will be drawn on the map,
     * in order to join each track together.
     *
     * @param gpxFiles a stream of GPX files to concatenate together
     * @param dir the working dir
     * @throws IOException if there is a problem while reading/writing any of the files
     */
    public static void concatGpxFiles(Stream<File> gpxFiles, Path dir) throws IOException {
        GPX.Builder gpxBuilder = GPX.builder();
        gpxFiles
                .map(File::toPath)
                .map(GpxParser::readGpx)
                .flatMap(GPX::tracks)
                .sorted(getTrackComparator())
                .forEach(gpxBuilder::addTrack);
        GPX gpx = gpxBuilder.build();
        final var indent = new GPX.Writer.Indent(INDENT);
        Path tempGpxFile = Files.createFile(dir.resolve(UUID.randomUUID() + ".gpx"));
        GPX.Writer.of(indent).write(gpx, tempGpxFile);
    }

    private static Comparator<Track> getTrackComparator() {
        return (t1, t2) -> {
            Optional<WayPoint> firstWp1 = t1.segments().flatMap(TrackSegment::points).min(getWayPointComparator());
            Optional<WayPoint> firstWp2 = t2.segments().flatMap(TrackSegment::points).min(getWayPointComparator());
            Instant wpT1 = firstWp1.map(wp -> wp.getTime().orElse(Instant.EPOCH)).orElse(Instant.EPOCH);
            Instant wpT2 = firstWp2.map(wp -> wp.getTime().orElse(Instant.EPOCH)).orElse(Instant.EPOCH);
            return wpT1.compareTo(wpT2);
        };
    }

    private static Comparator<WayPoint> getWayPointComparator() {
        return (p1, p2) -> {
            Instant t1 = p1.getTime().orElse(Instant.EPOCH);
            Instant t2 = p2.getTime().orElse(Instant.EPOCH);
            return t1.compareTo(t2);
        };
    }

    private static GPX readGpx(Path path) {
        try {
            return GPX.read(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
