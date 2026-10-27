package com.lrceditor.mobile.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LrcFileNamesTest {

    @Test
    public void addsLrcExtensionWithoutDuplicatingTextSuffixes() {
        assertEquals("lyrics.lrc", LrcFileNames.withLrcExtension("lyrics"));
        assertEquals("lyrics.lrc", LrcFileNames.withLrcExtension("lyrics.txt"));
        assertEquals("lyrics.lrc", LrcFileNames.withLrcExtension("lyrics.lrc.txt"));
        assertEquals("song.version.lrc",
                LrcFileNames.withLrcExtension("song.version.lrc"));
    }

    @Test
    public void recognizesOnlyLrcAsFinalExtension() {
        assertTrue(LrcFileNames.hasLrcExtension("song.lrc"));
        assertTrue(LrcFileNames.hasLrcExtension("SONG.LRC"));
        assertFalse(LrcFileNames.hasLrcExtension("song.lrc.txt"));
        assertFalse(LrcFileNames.hasLrcExtension("song.txt"));
    }
}
