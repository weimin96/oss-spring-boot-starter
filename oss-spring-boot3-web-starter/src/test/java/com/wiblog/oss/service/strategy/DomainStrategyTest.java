package com.wiblog.oss.service.strategy;

import com.wiblog.oss.exception.OssException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 域名策略测试：VirtualHostedDomainStrategy / PathStyleDomainStrategy / DomainStrategyFactory
 */
@DisplayName("域名策略（策略模式）")
class DomainStrategyTest {

    // =========================================================
    // VirtualHostedDomainStrategy
    // =========================================================
    @Nested
    @DisplayName("VirtualHostedDomainStrategy（OBS/COS）")
    class VirtualHostedTest {

        private final VirtualHostedDomainStrategy strategy = new VirtualHostedDomainStrategy();

        @ParameterizedTest(name = "type={0} => supports")
        @ValueSource(strings = {"obs", "OBS", "cos", "COS"})
        @DisplayName("支持 obs / cos（大小写不敏感）")
        void supportsObsAndCos(String type) {
            assertThat(strategy.supports(type)).isTrue();
        }

        @ParameterizedTest(name = "type={0} => not supports")
        @ValueSource(strings = {"minio", "s3", "aliyun", ""})
        @DisplayName("不支持 minio / s3 等类型")
        void notSupportsOthers(String type) {
            assertThat(strategy.supports(type)).isFalse();
        }

        @Test
        @DisplayName("supports(null) 返回 false")
        void supportsNull() {
            assertThat(strategy.supports(null)).isFalse();
        }

        @Test
        @DisplayName("buildDomain 生成 Virtual-Hosted 格式域名")
        void buildDomainVirtualHosted() {
            String domain = strategy.buildDomain(
                    "https://obs.cn-north-4.myhuaweicloud.com", "my-bucket");
            assertThat(domain).isEqualTo("https://my-bucket.obs.cn-north-4.myhuaweicloud.com/");
        }

        @Test
        @DisplayName("buildDomain 保留 http 协议")
        void buildDomainHttpProtocol() {
            String domain = strategy.buildDomain("http://cos.ap-guangzhou.myqcloud.com", "test-bucket");
            assertThat(domain).startsWith("http://test-bucket.");
            assertThat(domain).endsWith("/");
        }

        @Test
        @DisplayName("buildDomain 传入非法 URL 抛出 OssException")
        void buildDomainInvalidUrl() {
            assertThatThrownBy(() -> strategy.buildDomain("not-a-url", "bucket"))
                    .isInstanceOf(OssException.class)
                    .hasMessageContaining("Malformed");
        }
    }

    // =========================================================
    // PathStyleDomainStrategy
    // =========================================================
    @Nested
    @DisplayName("PathStyleDomainStrategy（MinIO/默认）")
    class PathStyleTest {

        private final PathStyleDomainStrategy strategy = new PathStyleDomainStrategy();

        @ParameterizedTest(name = "type={0}")
        @ValueSource(strings = {"minio", "s3", "obs", "", "anything"})
        @DisplayName("supports() 对所有类型返回 true（兜底策略）")
        void supportsAll(String type) {
            assertThat(strategy.supports(type)).isTrue();
        }

        @Test
        @DisplayName("supports(null) 也返回 true")
        void supportsNull() {
            assertThat(strategy.supports(null)).isTrue();
        }

        @Test
        @DisplayName("buildDomain 生成 Path-Style 格式域名（endpoint 末尾无斜杠）")
        void buildDomainNoTrailingSlash() {
            String domain = strategy.buildDomain("http://localhost:9000", "photos");
            assertThat(domain).isEqualTo("http://localhost:9000/photos/");
        }

        @Test
        @DisplayName("buildDomain 生成 Path-Style 格式域名（endpoint 末尾已有斜杠）")
        void buildDomainWithTrailingSlash() {
            String domain = strategy.buildDomain("http://localhost:9000/", "photos");
            assertThat(domain).isEqualTo("http://localhost:9000/photos/");
        }

        @Test
        @DisplayName("域名始终以 '/' 结尾")
        void alwaysEndsWithSlash() {
            String domain = strategy.buildDomain("http://minio:9000", "bucket");
            assertThat(domain).endsWith("/");
        }
    }

    // =========================================================
    // DomainStrategyFactory
    // =========================================================
    @Nested
    @DisplayName("DomainStrategyFactory（工厂选择）")
    class FactoryTest {

        @ParameterizedTest(name = "type={0} => VirtualHosted")
        @ValueSource(strings = {"obs", "cos", "OBS", "COS"})
        @DisplayName("obs/cos 类型选取 VirtualHostedDomainStrategy")
        void obsAndCosGetVirtualHosted(String type) {
            DomainStrategy strategy = DomainStrategyFactory.getStrategy(type);
            assertThat(strategy).isInstanceOf(VirtualHostedDomainStrategy.class);
        }

        @ParameterizedTest(name = "type={0} => PathStyle")
        @ValueSource(strings = {"minio", "s3", "aliyun"})
        @DisplayName("其他类型回退到 PathStyleDomainStrategy")
        void othersGetPathStyle(String type) {
            DomainStrategy strategy = DomainStrategyFactory.getStrategy(type);
            assertThat(strategy).isInstanceOf(PathStyleDomainStrategy.class);
        }

        @Test
        @DisplayName("null 类型回退到 PathStyleDomainStrategy（兜底策略）")
        void nullTypeFallsBackToPathStyle() {
            DomainStrategy strategy = DomainStrategyFactory.getStrategy(null);
            assertThat(strategy).isInstanceOf(PathStyleDomainStrategy.class);
        }

        @Test
        @DisplayName("getStrategy 不返回 null")
        void neverReturnsNull() {
            assertThat(DomainStrategyFactory.getStrategy("minio")).isNotNull();
            assertThat(DomainStrategyFactory.getStrategy("obs")).isNotNull();
        }
    }
}
