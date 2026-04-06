package com.wiblog.oss.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.util.StringUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * MinIO 测试支撑。
 *
 * <p>集中封装容器启动与动态属性注册，是为了让 `service`、`controller` 两侧测试共享同一套
 * 对象存储接入方式，避免每个测试类各自维护一份环境初始化逻辑。</p>
 */
public final class MinioTestSupport {

    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2023-09-04T19-57-37Z";
    private static final String DEFAULT_ACCESS_KEY = "minioadmin";
    private static final String DEFAULT_SECRET_KEY = "minioadmin";
    private static final String DEFAULT_BUCKET_NAME = "oss-starter-integration";
    private static final MinioRuntime MINIO_RUNTIME = MinioRuntime.start();

    private MinioTestSupport() {
    }

    public static void registerServiceProperties(DynamicPropertyRegistry registry) {
        registerCommonProperties(registry);
        registry.add("oss.http.enable", () -> false);
    }

    public static void registerControllerProperties(DynamicPropertyRegistry registry) {
        registerCommonProperties(registry);
        registry.add("oss.http.enable", () -> true);
        registry.add("oss.http.prefix", () -> "");
    }

    private static void registerCommonProperties(DynamicPropertyRegistry registry) {
        registry.add("oss.enable", () -> true);
        registry.add("oss.type", () -> "minio");
        registry.add("oss.endpoint", MINIO_RUNTIME::endpoint);
        registry.add("oss.access-key", MINIO_RUNTIME::accessKey);
        registry.add("oss.secret-key", MINIO_RUNTIME::secretKey);
        registry.add("oss.bucket-name", MINIO_RUNTIME::bucketName);
        registry.add("oss.auto-create-bucket", () -> true);
    }

    static final class MinioRuntime implements AutoCloseable {

        private static final String ENDPOINT_ENV = "OSS_TEST_MINIO_ENDPOINT";
        private static final String ACCESS_KEY_ENV = "OSS_TEST_MINIO_ACCESS_KEY";
        private static final String SECRET_KEY_ENV = "OSS_TEST_MINIO_SECRET_KEY";
        private static final String BUCKET_ENV = "OSS_TEST_MINIO_BUCKET";

        private final String endpoint;
        private final String accessKey;
        private final String secretKey;
        private final String bucketName;
        private final GenericContainer<?> container;

        private MinioRuntime(String endpoint, String accessKey, String secretKey,
                             String bucketName, GenericContainer<?> container) {
            this.endpoint = endpoint;
            this.accessKey = accessKey;
            this.secretKey = secretKey;
            this.bucketName = bucketName;
            this.container = container;
        }

        static MinioRuntime start() {
            String externalEndpoint = System.getenv(ENDPOINT_ENV);
            if (StringUtils.hasText(externalEndpoint)) {
                return new MinioRuntime(
                        externalEndpoint,
                        defaultIfBlank(System.getenv(ACCESS_KEY_ENV), DEFAULT_ACCESS_KEY),
                        defaultIfBlank(System.getenv(SECRET_KEY_ENV), DEFAULT_SECRET_KEY),
                        defaultIfBlank(System.getenv(BUCKET_ENV), DEFAULT_BUCKET_NAME),
                        null
                );
            }

            try {
                GenericContainer<?> minioContainer = new GenericContainer<>(DockerImageName.parse(MINIO_IMAGE))
                        .withEnv("MINIO_ROOT_USER", DEFAULT_ACCESS_KEY)
                        .withEnv("MINIO_ROOT_PASSWORD", DEFAULT_SECRET_KEY)
                        .withCommand("server", "/data")
                        .withExposedPorts(9000)
                        .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000))
                        .withStartupTimeout(Duration.ofMinutes(2));
                minioContainer.start();
                return new MinioRuntime(
                        "http://" + minioContainer.getHost() + ":" + minioContainer.getMappedPort(9000),
                        DEFAULT_ACCESS_KEY,
                        DEFAULT_SECRET_KEY,
                        DEFAULT_BUCKET_NAME,
                        minioContainer
                );
            } catch (Exception exception) {
                throw new IllegalStateException(
                        "本地未提供外部 MinIO，且 Testcontainers 启动 MinIO 失败。请确认 Docker 可用，或设置 OSS_TEST_MINIO_ENDPOINT 等环境变量。",
                        exception
                );
            }
        }

        String endpoint() {
            return endpoint;
        }

        String accessKey() {
            return accessKey;
        }

        String secretKey() {
            return secretKey;
        }

        String bucketName() {
            return bucketName;
        }

        @Override
        public void close() {
            if (container != null) {
                container.stop();
            }
        }

        private static String defaultIfBlank(String value, String defaultValue) {
            return StringUtils.hasText(value) ? value : defaultValue;
        }
    }
}
