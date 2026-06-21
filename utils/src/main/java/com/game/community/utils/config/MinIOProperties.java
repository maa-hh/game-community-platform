package com.game.community.utils.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 配置
 */
@Data
@ConfigurationProperties(prefix = "minio")
public class MinIOProperties {

    private String endpoint;

    private String publicEndpoint;

    private String accessKey;

    private String secretKey;

    private String publicBucketName;

    private String privateBucketName;

    private String publicFilePrefix = "user-files/public";

    private String privateFilePrefix = "user-files/pending";

    private int presignedExpireSeconds = 900;
}
