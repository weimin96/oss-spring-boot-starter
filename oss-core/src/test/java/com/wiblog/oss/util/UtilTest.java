package com.wiblog.oss.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UtilTest {

    @Test
    void normalizeObjectKeyDoesNotConvertExtensionlessNameToDirectory() {
        assertEquals("README", Util.normalizeObjectKey("README"));
        assertEquals("LICENSE", Util.normalizeObjectKey("/LICENSE"));
        assertEquals("docs/Dockerfile", Util.normalizeObjectKey("\\docs\\Dockerfile"));
        assertEquals("avatar", Util.normalizeObjectKey("avatar"));
    }

    @Test
    void formatPathStillFormatsDirectoryPrefix() {
        assertEquals("docs/", Util.formatPath("docs"));
        assertEquals("docs/readme.txt", Util.formatPath("docs/readme.txt"));
    }

    @Test
    void normalizeObjectPrefixAlwaysKeepsDirectoryBoundary() {
        assertEquals("release.v1/", Util.normalizeObjectPrefix("release.v1"));
        assertEquals("release.v1/", Util.normalizeObjectPrefix("/release.v1/"));
        assertEquals("docs/api/", Util.normalizeObjectPrefix("\\docs\\api"));
        assertEquals("", Util.normalizeObjectPrefix("/"));
    }
}
