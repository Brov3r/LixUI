package com.lixui.text;

import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Discovers font faces installed in the operating system.
 * Windows font directories, macOS font libraries and the common Linux font locations are scanned
 * on demand. Family and full face names are read from the OpenType name table, and file names are
 * indexed as aliases so callers can look up fonts by family, for example {@code "Segoe UI"}, by
 * full name, for example {@code "Segoe UI Bold"}, or by file stem, for example {@code "segoeuib"}.
 */
public final class SystemFonts {
    private static final Logger LOGGER = LoggerFactory.getLogger(SystemFonts.class);
    private static final int MICROSOFT_PLATFORM = 3;
    private static final int WINDOWS_UNICODE_BMP = 1;
    private static final int ENGLISH_LANGUAGE = 0x409;
    private static final int MAC_PLATFORM = 1;
    private static final int NAME_ID_FAMILY = 1;
    private static final int NAME_ID_SUBFAMILY = 2;
    private static final int NAME_ID_FULL_NAME = 4;
    private static final int NAME_ID_TYPOGRAPHIC_FAMILY = 16;
    private static final List<String> DEFAULT_FILE_NAMES = List.of(
            "segoeui.ttf", "arial.ttf", "tahoma.ttf", "verdana.ttf",
            "Helvetica.ttc", "HelveticaNeue.ttc", "Arial.ttf", "Menlo.ttc",
            "DejaVuSans.ttf", "LiberationSans-Regular.ttf", "NotoSans-Regular.ttf", "Ubuntu-R.ttf");
    private static final List<String> DEFAULT_FAMILIES = List.of(
            "Segoe UI", "Arial", "Helvetica Neue", "Helvetica",
            "DejaVu Sans", "Liberation Sans", "Noto Sans", "Tahoma", "Verdana");

    private static Index index;

    private SystemFonts() {
    }

    /**
     * Returns the installed font families in case-insensitive alphabetical order.
     * The first call scans the operating system font directories; later calls reuse the index.
     *
     * @return an unmodifiable list of family names
     */
    public static List<String> families() {
        return index().families();
    }

    /**
     * Loads a system font by family name or file stem.
     * The lookup is case-insensitive and ignores surrounding whitespace.
     *
     * @param family family name such as {@code "Arial"} or a file stem such as {@code "arial"}
     * @return the font bytes, or empty when no installed font matches
     * @throws NullPointerException if {@code family} is null
     */
    public static Optional<FontData> find(String family) {
        Objects.requireNonNull(family, "family");
        String key = normalize(family);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        Path path = index().paths().get(key);
        if (path == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(FontData.from(path));
        } catch (IOException exception) {
            LOGGER.warn("Failed to read system font {}", path, exception);
            return Optional.empty();
        }
    }

    /**
     * Loads an installed bold, italic or bold-italic face of a family.
     * The lookup matches full face names such as {@code "Arial Bold"} and {@code "Arial Italic"};
     * oblique faces are accepted as italics. A regular style is resolved with {@link #find(String)}.
     * The result is empty when the family has no installed face for the requested style, so callers
     * can fall back to a synthetic style.
     *
     * @param family family name such as {@code "Arial"}
     * @param style requested face style
     * @return the styled font bytes, or empty when no face is installed
     * @throws NullPointerException if an argument is null
     */
    public static Optional<FontData> find(String family, FontStyle style) {
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(style, "style");
        if (style == FontStyle.REGULAR) {
            return find(family);
        }
        for (String suffix : styleSuffixes(style)) {
            Optional<FontData> face = find(family.strip() + " " + suffix);
            if (face.isPresent()) {
                return face;
            }
        }
        return Optional.empty();
    }

    /**
     * Loads a suitable default system font.
     * Well-known faces are tried first, then the first indexed family. The result can be used
     * directly with {@link Font#load(FontData, float)}.
     *
     * @return the font bytes, or empty when the system exposes no readable TrueType font
     */
    public static Optional<FontData> findDefault() {
        for (String fileName : DEFAULT_FILE_NAMES) {
            for (Path directory : fontDirectories()) {
                Path candidate = directory.resolve(fileName);
                if (Files.isRegularFile(candidate)) {
                    try {
                        return Optional.of(FontData.from(candidate));
                    } catch (IOException exception) {
                        LOGGER.debug("Failed to read system font {}", candidate, exception);
                    }
                }
            }
        }
        for (String family : DEFAULT_FAMILIES) {
            Optional<FontData> found = find(family);
            if (found.isPresent()) {
                return found;
            }
        }
        return families().stream().findFirst().flatMap(SystemFonts::find);
    }

    private static synchronized Index index() {
        if (index == null) {
            index = scan(fontDirectories());
        }
        return index;
    }

    static Index scan(List<Path> directories) {
        Map<String, Path> paths = new HashMap<>();
        Map<String, Path> regularFaces = new HashMap<>();
        Map<String, Path> familyFallbacks = new HashMap<>();
        SortedSet<String> families = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Path directory : directories) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(directory)) {
                for (Path file : stream.filter(Files::isRegularFile).filter(SystemFonts::isFontFile).toList()) {
                    FontNames names = readNames(file);
                    if (names == null) {
                        continue;
                    }
                    String family = normalize(names.family());
                    familyFallbacks.putIfAbsent(family, file);
                    if (names.subfamily() != null) {
                        String subfamily = normalize(names.subfamily());
                        paths.putIfAbsent(family + " " + subfamily, file);
                        if (subfamily.equals("regular") || subfamily.equals("normal")
                                || subfamily.equals("roman") || subfamily.equals("book")) {
                            regularFaces.putIfAbsent(family, file);
                        }
                    }
                    if (names.fullName() != null) {
                        paths.putIfAbsent(normalize(names.fullName()), file);
                    }
                    paths.putIfAbsent(normalize(fileStem(file)), file);
                    families.add(names.family());
                }
            } catch (IOException | UncheckedIOException exception) {
                LOGGER.debug("Failed to scan font directory {}", directory, exception);
            }
        }
        familyFallbacks.forEach(paths::putIfAbsent);
        paths.putAll(regularFaces);
        return new Index(Map.copyOf(paths), List.copyOf(families));
    }

    private static FontNames readNames(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
            data.put(bytes).flip();
            STBTTFontinfo info = STBTTFontinfo.create();
            int offset = STBTruetype.stbtt_GetFontOffsetForIndex(data, 0);
            if (offset < 0 || !STBTruetype.stbtt_InitFont(info, data, offset)) {
                return null;
            }
            String family = readName(info, MICROSOFT_PLATFORM, WINDOWS_UNICODE_BMP,
                    ENGLISH_LANGUAGE, NAME_ID_FAMILY);
            if (family == null) {
                family = readName(info, MICROSOFT_PLATFORM, WINDOWS_UNICODE_BMP, 0, NAME_ID_FAMILY);
            }
            if (family == null) {
                family = readName(info, MAC_PLATFORM, 0, 0, NAME_ID_FAMILY);
            }
            if (family == null) {
                family = readName(info, MICROSOFT_PLATFORM, WINDOWS_UNICODE_BMP,
                        ENGLISH_LANGUAGE, NAME_ID_TYPOGRAPHIC_FAMILY);
            }
            if (family == null) {
                return null;
            }
            String fullName = readName(info, MICROSOFT_PLATFORM, WINDOWS_UNICODE_BMP,
                    ENGLISH_LANGUAGE, NAME_ID_FULL_NAME);
            String subfamily = readName(info, MICROSOFT_PLATFORM, WINDOWS_UNICODE_BMP,
                    ENGLISH_LANGUAGE, NAME_ID_SUBFAMILY);
            if (subfamily == null) {
                subfamily = readName(info, MAC_PLATFORM, 0, 0, NAME_ID_SUBFAMILY);
            }
            return new FontNames(family, subfamily, fullName);
        } catch (IOException exception) {
            LOGGER.debug("Failed to read font file {}", file, exception);
            return null;
        }
    }

    private static String readName(STBTTFontinfo info, int platform, int encoding,
                                   int language, int nameId) {
        ByteBuffer name = STBTruetype.stbtt_GetFontNameString(
                info, platform, encoding, language, nameId);
        if (name == null) {
            return null;
        }
        byte[] bytes = new byte[name.remaining()];
        name.get(bytes);
        String value = new String(bytes, platform == MICROSOFT_PLATFORM
                ? StandardCharsets.UTF_16BE : StandardCharsets.ISO_8859_1);
        value = value.replace("\u0000", "").strip();
        return value.isEmpty() ? null : value;
    }

    private static List<Path> fontDirectories() {
        String operatingSystem = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<Path> directories = new ArrayList<>();
        if (operatingSystem.contains("win")) {
            addEnvironmentPath(directories, "WINDIR", "Fonts");
            addEnvironmentPath(directories, "LOCALAPPDATA", "Microsoft", "Windows", "Fonts");
        } else if (operatingSystem.contains("mac")) {
            directories.add(Path.of("/System/Library/Fonts"));
            directories.add(Path.of("/Library/Fonts"));
            addHomePath(directories, "Library", "Fonts");
        } else {
            directories.add(Path.of("/usr/share/fonts"));
            directories.add(Path.of("/usr/local/share/fonts"));
            addHomePath(directories, ".local", "share", "fonts");
            addHomePath(directories, ".fonts");
        }
        return directories;
    }

    private static void addEnvironmentPath(List<Path> directories, String variable, String... children) {
        String value = System.getenv(variable);
        if (value != null && !value.isBlank()) {
            directories.add(Path.of(value, children));
        }
    }

    private static void addHomePath(List<Path> directories, String... children) {
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            directories.add(Path.of(home, children));
        }
    }

    private static boolean isFontFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".ttc");
    }

    private static String fileStem(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static List<String> styleSuffixes(FontStyle style) {
        return switch (style) {
            case BOLD -> List.of("Bold");
            case ITALIC -> List.of("Italic", "Oblique");
            case BOLD_ITALIC -> List.of("Bold Italic", "Bold Oblique");
            default -> throw new IllegalStateException("Unknown font style.");
        };
    }

    record Index(Map<String, Path> paths, List<String> families) {
    }

    private record FontNames(String family, String subfamily, String fullName) {
    }
}
