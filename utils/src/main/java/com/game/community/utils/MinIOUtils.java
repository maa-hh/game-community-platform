package com.game.community.utils;

import com.game.community.utils.config.MinIOProperties;
import io.minio.BucketExistsArgs;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.CopySource;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import io.minio.messages.Item;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * MinIO 文件工具
 */
@Component
@EnableConfigurationProperties(MinIOProperties.class)
public class MinIOUtils {

    private final MinIOProperties properties;
    private final MinioClient minioClient;
    private volatile boolean privateBucketReady;
    private volatile boolean publicBucketReady;

    public MinIOUtils(MinIOProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    public String uploadPrivateAvatar(MultipartFile file, String originalFilename) {
        return uploadPrivateFile(file, originalFilename, "avatar");
    }

    /**
     * 上传至公共桶（已发布资源），返回公网 URL
     */
    public String uploadPublicFile(MultipartFile file, String originalFilename, String folder) {
        try {
            MinioClient client = client();
            ensurePublicBucket(client);
            String suffix = extractSuffix(originalFilename);
            String normalizedFolder = StringUtils.hasText(folder) ? folder.trim() : "content";
            String objectName = properties.getPublicFilePrefix() + "/" + normalizedFolder + "/"
                    + UUID.randomUUID() + suffix;
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getPublicBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return buildPublicUrl(objectName);
        } catch (Exception e) {
            throw new IllegalStateException("公共文件上传失败", e);
        }
    }

    /**
     * 上传至私有桶（待审/草稿媒体），返回 objectName（非公网 URL）
     */
    public String uploadPrivateFile(MultipartFile file, String originalFilename, String folder) {
        try {
            MinioClient client = client();
            ensurePrivateBucket(client);
            String suffix = extractSuffix(originalFilename);
            String normalizedFolder = StringUtils.hasText(folder) ? folder.trim() : "content";
            String objectName = properties.getPrivateFilePrefix() + "/" + normalizedFolder + "/"
                    + UUID.randomUUID() + suffix;
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getPrivateBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return objectName;
        } catch (Exception e) {
            throw new IllegalStateException("私有文件上传失败", e);
        }
    }

    public void uploadPrivateBytes(String objectName, byte[] bytes, String contentType) {
        try {
            MinioClient client = client();
            ensurePrivateBucket(client);
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getPrivateBucketName())
                    .object(objectName)
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("私有分片写入失败", e);
        }
    }

    public String generatePrivateUrl(String objectName) {
        return generatePrivateUrl(objectName, properties.getPresignedExpireSeconds());
    }

    public String generatePrivateUrl(String objectName, int expireSeconds) {
        try {
            ensurePrivateBucket(client());
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getPrivateBucketName())
                    .object(objectName)
                    .expiry(expireSeconds)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("生成临时访问地址失败", e);
        }
    }

    public String generatePrivateAvatarUrl(String objectName) {
        return generatePrivateUrl(objectName);
    }

    /**
     * 将私有对象提升到公共桶，返回公网 URL
     */
    public String publishPrivateObject(String pendingObjectName, String publicFolder) {
        try {
            MinioClient client = client();
            ensurePrivateBucket(client);
            ensurePublicBucket(client);
            String suffix = pendingObjectName == null ? ""
                    : pendingObjectName.substring(pendingObjectName.lastIndexOf('/') + 1);
            String folder = StringUtils.hasText(publicFolder) ? publicFolder.trim() : "content";
            String publicObjectName = properties.getPublicFilePrefix() + "/" + folder + "/" + suffix;
            client.copyObject(CopyObjectArgs.builder()
                    .bucket(properties.getPublicBucketName())
                    .object(publicObjectName)
                    .source(CopySource.builder()
                            .bucket(properties.getPrivateBucketName())
                            .object(pendingObjectName)
                            .build())
                    .build());
            return buildPublicUrl(publicObjectName);
        } catch (Exception e) {
            throw new IllegalStateException("发布文件到公共桶失败", e);
        }
    }

    public String publishPrivateAvatar(String pendingObjectName) {
        return publishPrivateObject(pendingObjectName, "avatar");
    }

    public void deletePrivateObject(String objectName) {
        removeObject(properties.getPrivateBucketName(), objectName);
    }

    public void deletePrivateAvatar(String objectName) {
        deletePrivateObject(objectName);
    }

    /** 删除私有桶某前缀下全部对象（分片清理） */
    public void deletePrivatePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return;
        }
        try {
            MinioClient client = client();
            ensurePrivateBucket(client);
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(properties.getPrivateBucketName())
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> result : results) {
                Item item = result.get();
                removeObject(properties.getPrivateBucketName(), item.objectName());
            }
        } catch (Exception e) {
            throw new IllegalStateException("清理私有前缀失败", e);
        }
    }

    /**
     * 合并私有分片为目标对象（Compose）
     */
    public void composePrivateObjects(String targetObjectName, List<String> sourceObjectNames) {
        try {
            MinioClient client = client();
            ensurePrivateBucket(client);
            List<ComposeSource> sources = new ArrayList<>();
            for (String source : sourceObjectNames) {
                sources.add(ComposeSource.builder()
                        .bucket(properties.getPrivateBucketName())
                        .object(source)
                        .build());
            }
            client.composeObject(ComposeObjectArgs.builder()
                    .bucket(properties.getPrivateBucketName())
                    .object(targetObjectName)
                    .sources(sources)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("分片合并失败", e);
        }
    }

    public FilePayload readPrivateObject(String objectName) {
        try {
            MinioClient client = client();
            ensurePrivateBucket(client);
            try (InputStream inputStream = client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getPrivateBucketName())
                    .object(objectName)
                    .build())) {
                return new FilePayload(readLimited(inputStream), null);
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取私有文件失败", e);
        }
    }

    /** 流式计算私有对象 MD5，用于合并后的后端完整性校验。 */
    public String calculatePrivateMd5(String objectName) {
        try {
            ensurePrivateBucket(client());
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream inputStream = client().getObject(GetObjectArgs.builder()
                    .bucket(properties.getPrivateBucketName())
                    .object(objectName)
                    .build())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                long total = 0;
                while ((read = inputStream.read(buffer)) != -1) {
                    total += read;
                    if (total > properties.getMaxHashBytes()
                            && properties.getMaxHashBytes() > 0) {
                        throw new IllegalStateException("文件超过后端校验上限");
                    }
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder result = new StringBuilder(32);
            for (byte value : digest.digest()) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算文件 MD5 失败", e);
        }
    }

    public void deletePublicAvatarByUrl(String fileUrl) {
        deletePublicFileByUrl(fileUrl);
    }

    public void deletePublicFileByUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        String objectName = parseObjectName(fileUrl, properties.getPublicBucketName());
        removeObject(properties.getPublicBucketName(), objectName);
    }

    public FilePayload readFileByUrl(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new IllegalArgumentException("文件地址不能为空");
        }
        // pending://objectKey — 私有对象
        if (fileUrl.startsWith("pending://")) {
            return readPrivateObject(fileUrl.substring("pending://".length()));
        }
        try {
            URL url = new URL(fileUrl);
            if (!isAllowedDownloadHost(url)) {
                throw new IllegalArgumentException("不允许回源读取该文件地址");
            }
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            String contentType = connection.getContentType();
            try (InputStream inputStream = connection.getInputStream();
                 ) {
                return new FilePayload(readLimited(inputStream), contentType);
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取文件失败", e);
        }
    }

    private static String extractSuffix(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        return "";
    }

    private MinioClient client() {
        return minioClient;
    }

    private void ensurePrivateBucket(MinioClient client) throws Exception {
        if (privateBucketReady) {
            return;
        }
        synchronized (this) {
            if (!privateBucketReady) {
                ensureBucket(client, properties.getPrivateBucketName(), false);
                privateBucketReady = true;
            }
        }
    }

    private void ensurePublicBucket(MinioClient client) throws Exception {
        if (publicBucketReady) {
            return;
        }
        synchronized (this) {
            if (!publicBucketReady) {
                ensureBucket(client, properties.getPublicBucketName(), true);
                publicBucketReady = true;
            }
        }
    }

    private void ensureBucket(MinioClient client, String bucketName, boolean publicRead) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder()
                .bucket(bucketName)
                .build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder()
                    .bucket(bucketName)
                    .build());
        }
        if (publicRead) {
            client.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucketName)
                    .config(publicReadPolicy(bucketName))
                    .build());
        }
    }

    private String publicReadPolicy(String bucketName) {
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/*"]
                    }
                  ]
                }
                """.formatted(bucketName);
    }

    private String buildPublicObjectName(String pendingObjectName) {
        String suffix = pendingObjectName == null ? "" : pendingObjectName.substring(pendingObjectName.lastIndexOf('/') + 1);
        return properties.getPublicFilePrefix() + "/" + suffix;
    }

    private void removeObject(String bucketName, String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        try {
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("文件删除失败", e);
        }
    }

    private String parseObjectName(String fileUrl, String bucketName) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        String objectName = removeKnownPrefix(fileUrl, properties.getEndpoint(), bucketName);
        objectName = removeKnownPrefix(objectName, properties.getPublicEndpoint(), bucketName);
        if (!objectName.equals(fileUrl)) {
            return objectName;
        }
        try {
            String path = URI.create(fileUrl).getPath();
            String bucketPrefix = "/" + bucketName + "/";
            if (path.startsWith(bucketPrefix)) {
                return URLDecoder.decode(path.substring(bucketPrefix.length()), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // 尝试按对象名处理。
        }
        return fileUrl.startsWith("http://") || fileUrl.startsWith("https://") ? null : fileUrl;
    }

    private String buildPublicUrl(String objectName) {
        String endpoint = StringUtils.hasText(properties.getPublicEndpoint())
                ? properties.getPublicEndpoint()
                : properties.getEndpoint();
        return trimTrailingSlash(endpoint) + "/" + properties.getPublicBucketName() + "/" + objectName;
    }

    private byte[] readLimited(InputStream inputStream) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[64 * 1024];
            long total = 0;
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (properties.getMaxReadBytes() > 0 && total > properties.getMaxReadBytes()) {
                    throw new IllegalStateException("文件超过读取上限");
                }
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private boolean isAllowedDownloadHost(URL url) {
        if (!"http".equalsIgnoreCase(url.getProtocol())
                && !"https".equalsIgnoreCase(url.getProtocol())) {
            return false;
        }
        String host = url.getHost();
        if (properties.getAllowedDownloadHosts() != null
                && !properties.getAllowedDownloadHosts().isEmpty()) {
            return properties.getAllowedDownloadHosts().stream().anyMatch(host::equalsIgnoreCase);
        }
        return hostMatches(host, properties.getEndpoint())
                || hostMatches(host, properties.getPublicEndpoint());
    }

    private boolean hostMatches(String host, String endpoint) {
        try {
            return StringUtils.hasText(endpoint) && host.equalsIgnoreCase(URI.create(endpoint).getHost());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String removeKnownPrefix(String fileUrl, String endpoint, String bucketName) {
        if (!StringUtils.hasText(fileUrl) || !StringUtils.hasText(endpoint)) {
            return fileUrl;
        }
        String prefix = trimTrailingSlash(endpoint) + "/" + bucketName + "/";
        return fileUrl.startsWith(prefix) ? fileUrl.substring(prefix.length()) : fileUrl;
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record FilePayload(byte[] bytes, String contentType) {
    }
}
