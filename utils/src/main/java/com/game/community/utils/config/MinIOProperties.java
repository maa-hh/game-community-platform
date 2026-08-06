package com.game.community.utils.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

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

    /** 允许审核服务回源读取的域名；为空时仅允许 endpoint/publicEndpoint。 */
    private List<String> allowedDownloadHosts = new ArrayList<>();

    /** 审核/哈希读取的单对象上限，避免将异常大对象加载进堆。 */
    private long maxReadBytes = 16 * 1024 * 1024L;

    /** 合并文件完整性校验上限；0 表示不限制。 */
    private long maxHashBytes = 0;
}
