package com.wiblog.oss.service;

import com.wiblog.oss.bean.OssObjectEvent;
import com.wiblog.oss.exception.OssException;
import com.wiblog.oss.util.Util;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.protocols.jsoncore.JsonNode;
import software.amazon.awssdk.protocols.jsoncore.JsonNodeParser;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * MinIO 事件流解析器。
 *
 * @author panwm
 */
@Slf4j
public class MinioObjectEventParser {

    private final JsonNodeParser parser = JsonNodeParser.create();

    /**
     * 解析 MinIO ListenNotification 返回的一行事件 JSON。
     *
     * @param eventLine 事件 JSON 行
     * @return 对象变化事件列表
     */
    public List<OssObjectEvent> parse(String eventLine) {
        if (Util.isBlank(eventLine)) {
            return Collections.emptyList();
        }
        JsonNode root;
        try {
            root = parser.parse(eventLine);
        } catch (RuntimeException exception) {
            throw new OssException("OSS_EVENT_PARSE_FAILED", "解析对象事件 JSON 失败", exception);
        }

        Optional<JsonNode> recordsNode = root.field("Records");
        if (!recordsNode.isPresent() || !recordsNode.get().isArray()) {
            throw new OssException("OSS_EVENT_PARSE_FAILED", "对象事件缺少 Records 数组");
        }

        List<OssObjectEvent> events = new ArrayList<OssObjectEvent>();
        for (JsonNode record : recordsNode.get().asArray()) {
            OssObjectEvent event = parseRecord(record);
            if (event != null) {
                events.add(event);
            }
        }
        return events;
    }

    private OssObjectEvent parseRecord(JsonNode record) {
        String bucketName = text(record, "s3", "bucket", "name");
        String objectKey = text(record, "s3", "object", "key");
        if (Util.isBlank(objectKey)) {
            log.warn("跳过缺少对象键的 OSS 事件：{}", record.text());
            return null;
        }
        return OssObjectEvent.builder()
                .bucketName(bucketName)
                .objectKey(decodeObjectKey(objectKey))
                .eventName(text(record, "eventName"))
                .eventTime(text(record, "eventTime"))
                .size(number(record, "s3", "object", "size"))
                .eTag(text(record, "s3", "object", "eTag"))
                .versionId(text(record, "s3", "object", "versionId"))
                .sequencer(text(record, "s3", "object", "sequencer"))
                .build();
    }

    private String text(JsonNode node, String... path) {
        JsonNode current = find(node, path);
        if (current == null || current.isNull()) {
            return null;
        }
        if (current.isString()) {
            return current.asString();
        }
        if (current.isNumber()) {
            return current.asNumber();
        }
        if (current.isBoolean()) {
            return String.valueOf(current.asBoolean());
        }
        return current.text();
    }

    private Long number(JsonNode node, String... path) {
        JsonNode current = find(node, path);
        if (current == null || current.isNull()) {
            return null;
        }
        try {
            if (current.isNumber()) {
                return Long.valueOf(current.asNumber());
            }
            if (current.isString()) {
                return Long.valueOf(current.asString());
            }
        } catch (NumberFormatException exception) {
            throw new OssException("OSS_EVENT_PARSE_FAILED", "对象事件大小字段非法", exception);
        }
        return null;
    }

    private JsonNode find(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            if (current == null || !current.isObject()) {
                return null;
            }
            Optional<JsonNode> next = current.field(part);
            if (!next.isPresent()) {
                return null;
            }
            current = next.get();
        }
        return current;
    }

    private String decodeObjectKey(String objectKey) {
        try {
            return URLDecoder.decode(objectKey, "UTF-8");
        } catch (UnsupportedEncodingException exception) {
            throw new OssException("OSS_EVENT_PARSE_FAILED", "对象键解码失败", exception);
        }
    }
}
