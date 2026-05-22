package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssObjectEvent;
import com.wiblog.oss.config.OssClientOptions;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.auth.signer.params.AwsS3V4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MinIO 对象事件监听容器。
 *
 * @author panwm
 */
@Slf4j
@SuppressWarnings("deprecation")
public class MinioObjectEventListenerContainer {

    private final OssClientOptions ossProperties;
    private final List<OssObjectEventListener> listeners;
    private final MinioObjectEventParser eventParser;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread listenerThread;
    private volatile HttpURLConnection currentConnection;

    public MinioObjectEventListenerContainer(OssClientOptions ossProperties,
                                             List<OssObjectEventListener> listeners) {
        this.ossProperties = ossProperties;
        this.listeners = listeners == null
                ? Collections.<OssObjectEventListener>emptyList()
                : listeners;
        this.eventParser = new MinioObjectEventParser();
    }

    /**
     * 启动 MinIO 事件监听线程。
     */
    public void start() {
        if (!ossProperties.getEvent().isEnable()) {
            return;
        }
        if (!"minio".equalsIgnoreCase(ossProperties.getType())) {
            log.info("跳过 OSS 事件监听，当前 oss.type 不是 minio：{}", ossProperties.getType());
            return;
        }
        if (listeners.isEmpty()) {
            log.info("跳过 OSS 事件监听，容器中不存在 OssObjectEventListener Bean");
            return;
        }
        validateConfiguration();
        if (!running.compareAndSet(false, true)) {
            return;
        }
        listenerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runListenerLoop();
            }
        }, "oss-minio-object-event-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        log.info("OSS MinIO 事件监听已启动，bucket={}", resolveBucketName());
    }

    /**
     * 停止 MinIO 事件监听线程。
     */
    public void stop() {
        if (!running.get() && listenerThread == null && currentConnection == null) {
            return;
        }
        running.set(false);
        HttpURLConnection connection = currentConnection;
        if (connection != null) {
            connection.disconnect();
        }
        Thread thread = listenerThread;
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join(2_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("等待 OSS MinIO 事件监听线程停止时被中断", exception);
            }
        }
        listenerThread = null;
        log.info("OSS MinIO 事件监听已停止");
    }

    private void runListenerLoop() {
        while (running.get()) {
            try {
                listenOnce();
            } catch (Exception exception) {
                if (running.get()) {
                    log.warn("OSS MinIO 事件监听连接已断开：{}", exception.getMessage(), exception);
                    sleepBeforeReconnect();
                }
            }
        }
    }

    private void listenOnce() throws IOException {
        HttpURLConnection connection = openConnection();
        currentConnection = connection;
        try {
            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new OssException("OSS_EVENT_CONNECT_FAILED",
                        "MinIO 事件监听连接失败，HTTP 状态码：" + statusCode + "，响应：" + readError(connection));
            }
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                dispatchLine(line);
            }
        } finally {
            currentConnection = null;
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection() throws IOException {
        SdkHttpFullRequest signedRequest = signRequest();
        URL url = signedRequest.getUri().toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, ossProperties.getConnectionTimeout()));
        connection.setReadTimeout(0);
        for (Map.Entry<String, List<String>> entry : signedRequest.headers().entrySet()) {
            if ("Host".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            for (String value : entry.getValue()) {
                connection.addRequestProperty(entry.getKey(), value);
            }
        }
        return connection;
    }

    private SdkHttpFullRequest signRequest() {
        URI endpoint = URI.create(ossProperties.getEndpoint());
        SdkHttpFullRequest.Builder requestBuilder = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.GET)
                .uri(endpoint)
                .encodedPath(buildBucketPath(endpoint, resolveBucketName()))
                .appendRawQueryParameter("prefix", valueOrEmpty(ossProperties.getEvent().getPrefix()))
                .appendRawQueryParameter("suffix", valueOrEmpty(ossProperties.getEvent().getSuffix()));
        for (String event : ossProperties.getEvent().getEvents()) {
            requestBuilder.appendRawQueryParameter("events", event);
        }

        AwsS3V4SignerParams signerParams = AwsS3V4SignerParams.builder()
                .awsCredentials(AwsBasicCredentials.create(ossProperties.getAccessKey(), ossProperties.getSecretKey()))
                .signingName("s3")
                .signingRegion(Region.US_EAST_1)
                .doubleUrlEncode(false)
                .normalizePath(false)
                .build();
        return AwsS3V4Signer.create().sign(requestBuilder.build(), signerParams);
    }

    private void dispatchLine(String line) {
        List<OssObjectEvent> events;
        try {
            events = eventParser.parse(line);
        } catch (OssException exception) {
            log.warn("OSS MinIO 事件解析失败：{}", exception.getMessage(), exception);
            return;
        }
        for (OssObjectEvent event : events) {
            dispatchEvent(event);
        }
    }

    private void dispatchEvent(OssObjectEvent event) {
        for (OssObjectEventListener listener : listeners) {
            try {
                listener.onObjectChanged(event);
            } catch (RuntimeException exception) {
                log.error("OSS 对象事件监听器处理失败，bucket={}，object={}，event={}",
                        event.getBucketName(), event.getObjectKey(), event.getEventName(), exception);
            }
        }
    }

    private String resolveBucketName() {
        String eventBucketName = ossProperties.getEvent().getBucketName();
        return Util.isBlank(eventBucketName) ? ossProperties.getBucketName() : eventBucketName;
    }

    private void validateConfiguration() {
        if (Util.isBlank(resolveBucketName())) {
            throw OssException.configInvalid("oss.event.bucket-name");
        }
        if (ossProperties.getEvent().getEvents() == null || ossProperties.getEvent().getEvents().isEmpty()) {
            throw OssException.configInvalid("oss.event.events");
        }
        if (ossProperties.getEvent().getReconnectInterval() == null
                || ossProperties.getEvent().getReconnectInterval().isNegative()
                || ossProperties.getEvent().getReconnectInterval().isZero()) {
            throw OssException.configInvalid("oss.event.reconnect-interval");
        }
    }

    private void sleepBeforeReconnect() {
        Duration interval = ossProperties.getEvent().getReconnectInterval();
        try {
            Thread.sleep(interval.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildBucketPath(URI endpoint, String bucketName) {
        String basePath = endpoint.getRawPath();
        if (Util.isBlank(basePath) || "/".equals(basePath)) {
            return "/" + bucketName;
        }
        if (basePath.endsWith("/")) {
            return basePath + bucketName;
        }
        return basePath + "/" + bucketName;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String readError(HttpURLConnection connection) throws IOException {
        InputStream errorStream = connection.getErrorStream();
        if (errorStream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }
}
