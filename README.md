# OSS Spring Boot3 Starter

[![Java CI](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/weimin96/oss-spring-boot-starter/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/weimin96/oss-spring-boot-starter/badge.svg?branch=main)](https://coveralls.io/github/weimin96/oss-spring-boot-starter?branch=main)
[![GitHub Release](https://img.shields.io/github/v/release/weimin96/oss-spring-boot-starter)](https://github.com/weimin96/oss-spring-boot-starter/releases/)
[![Maven Central Version](https://img.shields.io/maven-central/v/io.github.weimin96/oss-spring-boot3-starter)](https://repo1.maven.org/maven2/io/github/weimin96/oss-spring-boot3-starter/)
[![GitHub repo size](https://img.shields.io/github/repo-size/weimin96/oss-spring-boot-starter)](https://github.com/weimin96/oss-spring-boot-starter/releases/)
[![License](https://img.shields.io/:license-apache-brightgreen.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![Last Commit](https://img.shields.io/github/last-commit/weimin96/oss-spring-boot-starter.svg)](https://github.com/weimin96/oss-spring-boot-starter)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/weimin96/oss-spring-boot-starter.svg)](https://github.com/weimin96/oss-spring-boot-starter)

README: [English](README.md) | [中文](README-zh-CN.md)

Wiki: [Wiki](https://github.com/weimin96/oss-spring-boot-starter/wiki)

Front-end companion: [oss-spring-boot-starter-ui-demo](https://github.com/weimin96/oss-spring-boot-starter-ui-demo)

---

## Introduction

`oss-spring-boot-starter` is a Spring Boot 3 auto-configuration library for S3-compatible object storage. It wraps the **AWS SDK for Java v2** (with CRT-based async client and S3 Transfer Manager) and provides a clean, fluent Java API plus an optional set of ready-to-use REST endpoints.

Any S3-compatible service works out of the box:

| Provider | Type value |
|---|---|
| Amazon S3 | *(leave blank)* |
| Tencent Cloud COS | `cos` |
| Alibaba Cloud OSS | `oss` |
| Huawei Cloud OBS | `obs` |
| Qiniu Cloud Kodo | `qiniu` |
| JD Cloud OSS | `jd` |
| MinIO | `minio` |

---

## Requirements

- **JDK 21+**
- **Spring Boot 3.x**

> For Spring Boot 2.x, use the [`spring2` branch](https://github.com/weimin96/oss-spring-boot-starter/tree/spring2).

---

## Quick Start

### 1. Add Dependency

**Maven**
```xml
<dependency>
    <groupId>io.github.weimin96</groupId>
    <artifactId>oss-spring-boot3-starter</artifactId>
    <version>${lastVersion}</version>
</dependency>
```

**Gradle**
```gradle
dependencies {
    implementation 'io.github.weimin96:oss-spring-boot3-starter:${lastVersion}'
}
```

### 2. Configure `application.yml`

```yaml
oss:
  enable: true
  endpoint: https://s3.ap-northeast-1.amazonaws.com   # or your MinIO/COS/OBS endpoint
  access-key: YOUR_ACCESS_KEY
  secret-key: YOUR_SECRET_KEY
  bucket-name: my-bucket
  type: minio          # omit for AWS S3; set to minio / cos / obs etc.
  auto-create-bucket: false
  throughput-in-gbps: 20.0
  part-size-in-mb: 10
  http:
    enable: true       # expose built-in REST endpoints
    prefix: /api       # optional URL prefix, e.g. /api/oss/object
```

### 3. Inject and Use

```java
@Autowired
private OssTemplate ossTemplate;

// Upload a file
ossTemplate.put().putObject("images/", "photo.jpg", new File("/tmp/photo.jpg"));

// Download as InputStream
InputStream in = ossTemplate.query().getInputStream("images/photo.jpg");

// Generate a pre-signed download URL (1 hour)
String url = ossTemplate.presign().generateGetPresignedUrl("images/photo.jpg", Duration.ofHours(1));

// Stream-unzip a ZIP directly into OSS — no local disk I/O
UnzipResult result = ossTemplate.unzip().unzip("archives/data.zip", "extracted/");
```

---

## Configuration Reference

| Property | Type | Default | Description |
|---|---|---|---|
| `oss.enable` | boolean | `false` | Master switch. Must be `true` to activate. |
| `oss.endpoint` | String | — | S3-compatible service endpoint URL (**required**). |
| `oss.access-key` | String | — | Access key ID (**required**). |
| `oss.secret-key` | String | — | Secret access key (**required**). |
| `oss.bucket-name` | String | — | Default bucket name. |
| `oss.auto-create-bucket` | boolean | `false` | Create the bucket automatically if it does not exist. |
| `oss.type` | String | — | Provider hint: `minio`, `cos`, `obs`, `oss`, `qiniu`, etc. |
| `oss.max-connections` | int | `50` | Maximum concurrent HTTP connections. |
| `oss.connection-timeout` | long | `10000` | Connection timeout in milliseconds. |
| `oss.throughput-in-gbps` | double | `20.0` | Target throughput for the CRT async client (Gbps). |
| `oss.part-size-in-mb` | int | `10` | Minimum multipart upload part size in MB (S3 minimum is 5 MB). |
| `oss.http.enable` | boolean | `false` | Enable built-in REST endpoints. |
| `oss.http.prefix` | String | `""` | URL prefix prepended to all built-in endpoints. |

---

## Java API

All operations are accessed via `OssTemplate` using a fluent namespace pattern:

```
ossTemplate.put()      → PutOperations      (upload, copy, move, multipart)
ossTemplate.query()    → QueryOperations     (list, tree, download, preview)
ossTemplate.delete()   → DeleteOperations    (single, batch, folder)
ossTemplate.unzip()    → StreamUnzipOperations (ZIP streaming decompression)
ossTemplate.presign()  → PresignOperations   (pre-signed GET / PUT URLs)
ossTemplate.tagging()  → TaggingOperations   (object & bucket tags)
ossTemplate.bucket()   → BucketOperations    (versioning, lifecycle, CORS, policy, encryption)
```

### Upload (`put()`)

```java
// Upload from InputStream
ossTemplate.put().putObject("folder/", "file.txt", inputStream);

// Upload from File
ossTemplate.put().putObjectForKey("images/photo.jpg", new File("/tmp/photo.jpg"));

// Create an empty folder placeholder
ossTemplate.put().mkdirs("folder/subfolder/");

// Upload an entire local directory
ossTemplate.put().putFolder("remote/path/", new File("/local/dir"), true);

// Copy within the same bucket
ossTemplate.put().copyFile("src/a.txt", "dst/a.txt");

// Move (copy + delete source)
ossTemplate.put().move("src/a.txt", "dst/");
```

### Multipart Upload (`put()`)

```java
// 1. Initialize
String uploadId = ossTemplate.put().initTask(chunkTask);

// 2. Upload each part (can be parallel)
ChunkTarget part = ossTemplate.put().chunk(chunk);

// 3. Complete
ObjectInfo info = ossTemplate.put().merge(chunkMerge);

// Query already-uploaded parts (for resumable upload)
List<Part> parts = ossTemplate.put().listParts(bucketName, objectKey, uploadId);
```

### Query & Download (`query()`)

```java
// Check existence
boolean exists = ossTemplate.query().checkExist("images/photo.jpg");

// Get metadata
ObjectInfo info = ossTemplate.query().getObjectInfo("images/photo.jpg");

// List all objects under a path
List<ObjectInfo> list = ossTemplate.query().listObjects("images/");

// Next-level listing (like `ls`, non-recursive)
List<ObjectTreeNode> nodes = ossTemplate.query().listNextLevel("images/");

// Paginated lazy list
LazyDataList<ObjectInfo> page = ossTemplate.query().lazyList("images/", 100, continuationToken);

// Full directory tree
ObjectTreeNode tree = ossTemplate.query().getTreeList("images/");

// Search tree by keyword
ObjectTreeNode result = ossTemplate.query().getTreeListByName("images/", "avatar");

// Read content as String
String text = ossTemplate.query().getContent("config/app.json");

// Download to local file
File f = ossTemplate.query().getFile("images/photo.jpg", "/tmp/photo.jpg");

// Download entire folder
ossTemplate.query().getFolder("images/", "/tmp/images/");

// Serve file to HTTP response (inline preview / attachment download)
ossTemplate.query().previewObject(request, response, "images/photo.jpg", false);
ossTemplate.query().previewObject(request, response, "video/demo.mp4", true);
```

### Delete (`delete()`)

```java
// Delete single object
ossTemplate.delete().removeObject("images/photo.jpg");

// Delete all objects under a folder (recursive)
ossTemplate.delete().removeFolder("tmp/uploads/");
```

### Stream Unzip (`unzip()`)

Streams a ZIP from S3 and writes each entry back to S3 without touching local disk.

```java
// Unzip to a target path in the same bucket
UnzipResult result = ossTemplate.unzip().unzip("archives/data.zip", "extracted/");

// Cross-bucket unzip
UnzipResult result = ossTemplate.unzip().unzip("src-bucket", "data.zip", "dst-bucket", "out/");

// Partial unzip — only entries whose name starts with "docs/"
UnzipResult result = ossTemplate.unzip().unzipWithFilter("data.zip", "docs/", "extracted/docs/");

// Custom handler — e.g. pipe entry directly to an HTTP response
ossTemplate.unzip().unzip("data.zip", (entry, stream) -> {
    // process stream without closing it
    Files.copy(stream, Paths.get("/tmp/" + entry.getName()));
});

System.out.println(result.getSucceededCount()); // number of extracted files
System.out.println(result.getFailed());         // list of failed entry names
```

### Pre-signed URLs (`presign()`)

```java
// Generate a temporary download link (expires in 30 minutes)
String downloadUrl = ossTemplate.presign()
    .generateGetPresignedUrl("images/photo.jpg", Duration.ofMinutes(30));

// Generate a client-side direct-upload URL (PUT, 15 minutes)
String uploadUrl = ossTemplate.presign()
    .generatePutPresignedUrl("uploads/new.jpg", "image/jpeg", Duration.ofMinutes(15), null);
```

### Object Tags (`tagging()`)

```java
// Set tags (overwrites all existing tags)
ossTemplate.tagging().setObjectTags("file.jpg", Map.of("env", "prod", "owner", "alice"));

// Merge tags (keeps existing, updates/adds specified keys)
ossTemplate.tagging().mergeObjectTags("file.jpg", Map.of("reviewed", "true"));

// Get tags
Map<String, String> tags = ossTemplate.tagging().getObjectTags("file.jpg");

// Delete all tags
ossTemplate.tagging().deleteObjectTags("file.jpg");

// Bucket-level tags
ossTemplate.tagging().setBucketTags(Map.of("project", "my-app"));
```

### Bucket Management (`bucket()`)

```java
// Versioning
ossTemplate.bucket().enableVersioning();
ossTemplate.bucket().suspendVersioning();
String status = ossTemplate.bucket().getVersioningStatus(); // "Enabled" / "Suspended" / null

// Lifecycle: auto-delete objects under "tmp/" after 7 days
ossTemplate.bucket().addExpirationRule("expire-tmp", "tmp/", 7);
List<LifecycleRule> rules = ossTemplate.bucket().getLifecycleRules();
ossTemplate.bucket().deleteLifecycleRules();

// CORS: allow all origins (for browser direct upload)
ossTemplate.bucket().allowAllOriginsCors();
ossTemplate.bucket().deleteCorsRules();

// Bucket policy
String policy = ossTemplate.bucket().getBucketPolicy();
ossTemplate.bucket().putBucketPolicy(policyJson);
ossTemplate.bucket().deleteBucketPolicy();

// Server-side encryption (SSE-S3 / AES-256)
ossTemplate.bucket().enableServerSideEncryption();

// Block all public access
ossTemplate.bucket().blockAllPublicAccess();
```

---

## Built-in REST Endpoints

Enable with `oss.http.enable: true`. All paths are prefixed by `${oss.http.prefix}/oss`.

### File Upload

| Method | Path | Description |
|---|---|---|
| `POST` | `/oss/object` | Upload a single file |
| `POST` | `/oss/folder` | Create a folder placeholder |
| `POST` | `/oss/multipart/init` | Initialize multipart upload |
| `POST` | `/oss/multipart/chunk` | Upload a part |
| `POST` | `/oss/multipart/merge` | Complete multipart upload |
| `GET` | `/oss/multipart/parts` | List uploaded parts (resumable upload) |

### File Query

| Method | Path | Description |
|---|---|---|
| `GET` | `/oss/object` | Get object metadata |
| `GET` | `/oss/object/exists` | Check if object exists |
| `GET` | `/oss/object/list` | List all objects under a path |
| `GET` | `/oss/object/list/next-level` | List next-level entries (non-recursive) |
| `GET` | `/oss/object/list/lazy` | Paginated object list |
| `GET` | `/oss/object/tree` | Full directory tree |
| `GET` | `/oss/object/tree/search` | Search tree by keyword |
| `GET` | `/oss/object/tree/folder` | Folder-only tree |
| `GET` | `/oss/buckets` | List all buckets |
| `GET` | `/oss/connect` | Test OSS connectivity |

### File Preview & Download

| Method | Path | Description |
|---|---|---|
| `GET` | `/oss/object/preview/**` | Inline preview (supports HTTP Range) |
| `GET` | `/oss/object/download/**` | Force download as attachment |

### File Operations

| Method | Path | Description |
|---|---|---|
| `DELETE` | `/oss/object` | Delete a single object |
| `DELETE` | `/oss/objects` | Batch delete (JSON body: list of keys) |
| `DELETE` | `/oss/folder` | Recursively delete a folder |
| `POST` | `/oss/object/copy` | Copy object within the same bucket |
| `POST` | `/oss/object/move` | Move object to another folder |

### Stream Unzip

| Method | Path | Description |
|---|---|---|
| `POST` | `/oss/unzip` | Unzip a ZIP object to a target path |
| `POST` | `/oss/unzip/cross-bucket` | Cross-bucket unzip |
| `POST` | `/oss/unzip/filter` | Unzip with entry prefix filter |

### Pre-signed URLs

| Method | Path | Description |
|---|---|---|
| `GET` | `/oss/presign/get` | Generate pre-signed download URL |
| `GET` | `/oss/presign/put` | Generate pre-signed client-upload URL |

### Object Tags

| Method | Path | Description |
|---|---|---|
| `GET` | `/oss/object/tags` | Get object tags |
| `PUT` | `/oss/object/tags` | Set object tags (overwrite) |
| `PATCH` | `/oss/object/tags` | Merge/update object tags |
| `DELETE` | `/oss/object/tags` | Delete all object tags |

### Bucket Management

| Method | Path | Description |
|---|---|---|
| `POST` | `/oss/bucket` | Create a bucket |
| `GET` | `/oss/bucket/versioning` | Get versioning status |
| `PUT` | `/oss/bucket/versioning/enable` | Enable versioning |
| `PUT` | `/oss/bucket/versioning/suspend` | Suspend versioning |
| `GET` | `/oss/bucket/lifecycle` | Get lifecycle rules |
| `POST` | `/oss/bucket/lifecycle/expiration` | Add expiration rule |
| `DELETE` | `/oss/bucket/lifecycle` | Delete all lifecycle rules |
| `GET` | `/oss/bucket/cors` | Get CORS configuration |
| `PUT` | `/oss/bucket/cors/allow-all` | Allow all origins CORS |
| `DELETE` | `/oss/bucket/cors` | Delete CORS configuration |
| `GET` | `/oss/bucket/policy` | Get bucket policy |
| `PUT` | `/oss/bucket/policy` | Set bucket policy |
| `DELETE` | `/oss/bucket/policy` | Delete bucket policy |
| `PUT` | `/oss/bucket/encryption/enable` | Enable SSE-S3 encryption |
| `PUT` | `/oss/bucket/public-access/block` | Block all public access |
| `GET` | `/oss/bucket/tags` | Get bucket tags |
| `PUT` | `/oss/bucket/tags` | Set bucket tags |
| `DELETE` | `/oss/bucket/tags` | Delete bucket tags |

---

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
