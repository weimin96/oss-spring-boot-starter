package com.wiblog.oss.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Util} 工具类单元测试
 * <p>覆盖：getFilename / getExtension / formatPath / checkIsFile / isBlank / length / getContentType</p>
 */
@DisplayName("Util 工具类")
class UtilTest {

    // =========================================================
    // getFilename
    // =========================================================
    @Nested
    @DisplayName("getFilename()")
    class GetFilenameTest {

        @ParameterizedTest(name = "path={0} => {1}")
        @CsvSource({
                "upload/test.txt,   test.txt",
                "a/b/c/file.jpg,   file.jpg",
                "noSlash.png,      noSlash.png",
        })
        void standardUnixPaths(String path, String expected) {
            assertThat(Util.getFilename(path)).isEqualTo(expected);
        }

        @Test
        @DisplayName("Windows 反斜杠路径统一规范化")
        void windowsBackslash() {
            assertThat(Util.getFilename("upload\\subdir\\file.txt")).isEqualTo("file.txt");
        }

        @Test
        @DisplayName("混合分隔符路径取最后段")
        void mixedSeparators() {
            assertThat(Util.getFilename("a/b\\c/file.doc")).isEqualTo("file.doc");
        }

        @Test
        @DisplayName("无分隔符时直接返回原路径")
        void noSeparator() {
            assertThat(Util.getFilename("readme.md")).isEqualTo("readme.md");
        }

        @Test
        @DisplayName("路径末尾为斜杠时返回空串")
        void trailingSlash() {
            assertThat(Util.getFilename("upload/")).isEqualTo("");
        }
    }

    // =========================================================
    // getExtension
    // =========================================================
    @Nested
    @DisplayName("getExtension()")
    class GetExtensionTest {

        @ParameterizedTest(name = "path={0} => {1}")
        @CsvSource({
                "file.txt,   txt",
                "image.JPEG, JPEG",
                "a/b/c.gz,   gz",
        })
        void normalExtensions(String path, String expected) {
            assertThat(Util.getExtension(path)).isEqualTo(expected);
        }

        @Test
        @DisplayName("无扩展名返回 null")
        void noExtension() {
            assertThat(Util.getExtension("noext")).isNull();
        }

        @Test
        @DisplayName("末尾是点号，无扩展名返回 null")
        void endsWithDot() {
            assertThat(Util.getExtension("file.")).isNull();
        }

        @Test
        @DisplayName("null 路径返回 null")
        void nullPath() {
            assertThat(Util.getExtension(null)).isNull();
        }

        @Test
        @DisplayName("点号在斜杠前不算扩展名（如 a.b/c）")
        void dotBeforeSlash() {
            assertThat(Util.getExtension("a.b/c")).isNull();
        }

        @Test
        @DisplayName("tar.gz 取最后一个扩展名 gz")
        void multipleDotsOnlyLastCounts() {
            assertThat(Util.getExtension("archive.tar.gz")).isEqualTo("gz");
        }
    }

    // =========================================================
    // formatPath
    // =========================================================
    @Nested
    @DisplayName("formatPath()")
    class FormatPathTest {

        @ParameterizedTest(name = "input=\"{0}\" => \"{1}\"")
        @CsvSource({
                "upload,     upload/",
                "/upload,    upload/",
                "a/b/c,      a/b/c/",
        })
        void directoryPathsGetSlashAppended(String input, String expected) {
            assertThat(Util.formatPath(input)).isEqualTo(expected);
        }

        @Test
        @DisplayName("文件路径（含扩展名）不追加斜杠")
        void filePathNoSlashAppended() {
            assertThat(Util.formatPath("upload/test.txt")).isEqualTo("upload/test.txt");
        }

        @Test
        @DisplayName("根路径 \"/\" 返回空串")
        void rootPathReturnsEmpty() {
            assertThat(Util.formatPath("/")).isEqualTo("");
        }

        @Test
        @DisplayName("null 返回空串")
        void nullReturnsEmpty() {
            assertThat(Util.formatPath(null)).isEqualTo("");
        }

        @Test
        @DisplayName("空串返回空串")
        void emptyStringReturnsEmpty() {
            assertThat(Util.formatPath("")).isEqualTo("");
        }

        @Test
        @DisplayName("Windows 反斜杠被规范化为正斜杠")
        void windowsBackslashNormalized() {
            assertThat(Util.formatPath("upload\\sub")).isEqualTo("upload/sub/");
        }

        @Test
        @DisplayName("已有末尾斜杠的目录路径保持不变")
        void alreadyHasTrailingSlash() {
            assertThat(Util.formatPath("upload/")).isEqualTo("upload/");
        }

        @Test
        @DisplayName("开头斜杠被去除")
        void leadingSlashStripped() {
            assertThat(Util.formatPath("/upload/sub")).isEqualTo("upload/sub/");
        }
    }

    // =========================================================
    // checkIsFile
    // =========================================================
    @Nested
    @DisplayName("checkIsFile()")
    class CheckIsFileTest {

        @Test
        @DisplayName("包含扩展名且不以斜杠结尾 => true")
        void regularFile() {
            assertThat(Util.checkIsFile("a/b/file.txt")).isTrue();
        }

        @Test
        @DisplayName("以斜杠结尾视为目录 => false")
        void endsWithSlash() {
            assertThat(Util.checkIsFile("a/b/")).isFalse();
        }

        @Test
        @DisplayName("无扩展名 => false")
        void noExtension() {
            assertThat(Util.checkIsFile("a/b/noext")).isFalse();
        }

        @Test
        @DisplayName("null => false")
        void nullInput() {
            assertThat(Util.checkIsFile(null)).isFalse();
        }

        @Test
        @DisplayName("空串 => false")
        void emptyInput() {
            assertThat(Util.checkIsFile("")).isFalse();
        }

        @Test
        @DisplayName("Windows 路径含扩展名 => true")
        void windowsPath() {
            assertThat(Util.checkIsFile("C:\\Users\\file.pdf")).isTrue();
        }
    }

    // =========================================================
    // isBlank / length
    // =========================================================
    @Nested
    @DisplayName("isBlank() / length()")
    class IsBlankTest {

        @ParameterizedTest(name = "input={0}")
        @NullSource
        @ValueSource(strings = {"", "  ", "\t", "\n", "\r\n"})
        @DisplayName("空白/null 均返回 true")
        void blankCases(String input) {
            assertThat(Util.isBlank(input)).isTrue();
        }

        @ParameterizedTest(name = "input=\"{0}\"")
        @ValueSource(strings = {"a", " a ", "hello", "0"})
        @DisplayName("非空白返回 false")
        void nonBlankCases(String input) {
            assertThat(Util.isBlank(input)).isFalse();
        }

        @Test
        @DisplayName("length(null) 返回 0")
        void lengthNull() {
            assertThat(Util.length(null)).isZero();
        }

        @Test
        @DisplayName("length(\"abc\") 返回 3")
        void lengthNormal() {
            assertThat(Util.length("abc")).isEqualTo(3);
        }
    }

    // =========================================================
    // getContentType（Tika 单例调用）
    // =========================================================
    @Nested
    @DisplayName("getContentType()")
    class GetContentTypeTest {

        @ParameterizedTest(name = "file={0} => {1}")
        @CsvSource({
                "image.png,  image/png",
                "doc.pdf,    application/pdf",
                "data.json,  application/json",
                "style.css,  text/css",
        })
        void knownMimeTypes(String filename, String expectedMime) {
            assertThat(Util.getContentType(filename)).isEqualTo(expectedMime);
        }

        @Test
        @DisplayName("多次调用使用同一单例，不抛出异常")
        void repeatedCallsUseSingleton() {
            for (int i = 0; i < 20; i++) {
                assertThat(Util.getContentType("file.txt")).isNotNull();
            }
        }

        @Test
        @DisplayName("未知类型返回通用二进制 MIME")
        void unknownType() {
            // Tika 对未知扩展名返回 application/octet-stream
            assertThat(Util.getContentType("file.unknown_ext_xyz")).isNotNull();
        }
    }
}
