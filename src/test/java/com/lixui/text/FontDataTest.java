package com.lixui.text;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FontDataTest {
    private static final byte[] BYTES = {0, 1, 0, 0, 4, 5, 6, 7};

    @Test
    void copiesRawBytes() {
        byte[] source = BYTES.clone();

        FontData data = FontData.of(source);
        source[0] = 99;

        assertEquals(BYTES.length, data.buffer().remaining());
        assertEquals(BYTES[0], data.buffer().get(0));
    }

    @Test
    void readsFilesStreamsAndClasspathResources(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("sample.ttf");
        Files.write(file, BYTES);

        assertEquals(BYTES.length, FontData.from(file).buffer().remaining());
        assertEquals(BYTES.length, FontData.from(new ByteArrayInputStream(BYTES)).buffer().remaining());
        assertThrows(IOException.class, () -> FontData.fromResource("/missing/font.ttf"));
    }

    @Test
    void rejectsEmptyAndMissingInput() {
        assertThrows(NullPointerException.class, () -> FontData.of(null));
        assertThrows(IllegalArgumentException.class, () -> FontData.of(new byte[0]));
        assertThrows(NullPointerException.class, () -> FontData.from((Path) null));
        assertThrows(NullPointerException.class, () -> FontData.from((InputStream) null));
        assertThrows(NullPointerException.class, () -> FontData.fromResource(null));
        assertThrows(IllegalArgumentException.class,
                () -> FontData.from(new ByteArrayInputStream(new byte[0])));
    }
}
