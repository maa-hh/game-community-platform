package com.game.community.utils;

import com.game.community.utils.config.MinIOProperties;
import io.minio.BucketExistsArgs;
import io.minio.CopySource;
import io.minio.CopyObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * MinIO 文件工具
 */
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(MinIOProperties.class)
public class MinIOUtils {

    private final MinIOProperties properties;

    public String uploadPrivateAvatar(MultipartFile file, String originalFilename) {
        try {
            MinioClient client = client();
            ensurePrivateBucket(client);
            String suffix = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = properties.getPrivateFilePrefix() + "/" + UUID.randomUUID() + suffix;
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getPrivateBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return objectName;
        } catch (Exception e) {
            throw new IllegalStateException("文件上传失败", e);
        }
    }

    public String uploadPublicFile(MultipartFile file, String originalFilename, String folder) {
        try {
            MinioClient client = client();
            ensurePublicBucket(client);
            String suffix = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String normalizedFolder = StringUtils.hasText(folder) ? folder.trim() : "content";
            String objectName = properties.getPublicFilePrefix() + "/" + normalizedFolder + "/" + UUID.randomUUID() + suffix;
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getPublicBucketName())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return buildPublicUrl(objectName);
        } catch (Exception e) {
            throw new IllegalStateException("文件上传失败", e);
        }
    }

    public String generatePrivateAvatarUrl(String objectName) {
        try {
            ensurePrivateBucket(client());
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.getPrivateBucketName())
                    .object(objectName)
                    .expiry(properties.getPresignedExpireSeconds())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("生成临时访问地址失败", e);
        }
    }

    public String publishPrivateAvatar(String pendingObjectName) {
        try {
            MinioClient client = client();
            ensurePrivateBucket(client);
            ensurePublicBucket(client);
            String publicObjectName = buildPublicObjectName(pendingObjectName);
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
            throw new IllegalStateException("发布头像失败", e);
        }
    }

    public void deletePrivateAvatar(String objectName) {
        removeObject(properties.getPrivateBucketName(), objectName);
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
        try {
            URLConnection connection = new URL(fileUrl).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            String contentType = connection.getContentType();
            try (InputStream inputStream = connection.getInputStream();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                inputStream.transferTo(outputStream);
                return new FilePayload(outputStream.toByteArray(), contentType);
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取文件失败", e);
        }
    }

    private MinioClient client() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    private void ensurePrivateBucket(MinioClient client) throws Exception {
        ensureBucket(client, properties.getPrivateBucketName(), false);
    }

    private void ensurePublicBucket(MinioClient client) throws Exception {
        ensureBucket(client, properties.getPublicBucketName(), true);
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
