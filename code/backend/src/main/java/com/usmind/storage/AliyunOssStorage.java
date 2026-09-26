package com.usmind.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;

@Slf4j
public class AliyunOssStorage implements StorageStrategy {

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final String domain;

    public AliyunOssStorage(String endpoint, String accessKey, String secretKey,
                            String bucket, String domain) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.domain = domain;
    }

    @Override
    public String type() {
        return "aliyun";
    }

    private OSS build() {
        return new OSSClientBuilder().build(endpoint, accessKey, secretKey);
    }

    @Override
    public String upload(byte[] data, String storedName, String contentType) throws Exception {
        OSS oss = build();
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(data.length);
            if (contentType != null) meta.setContentType(contentType);
            PutObjectResult r = oss.putObject(bucket, storedName, new ByteArrayInputStream(data), meta);
            log.info("阿里云OSS上传成功: {}, etag={}", storedName, r == null ? "" : r.getETag());
            String base = (domain == null || domain.isEmpty()) ? ("https://" + bucket + "." + endpoint + "/") : domain;
            if (!base.endsWith("/")) base = base + "/";
            return base + storedName;
        } finally {
            oss.shutdown();
        }
    }

    @Override
    public void test() throws Exception {
        OSS oss = build();
        try {
            if (!oss.doesBucketExist(bucket)) {
                throw new IllegalStateException("bucket 不存在: " + bucket);
            }
        } finally {
            oss.shutdown();
        }
    }

    @Override
    public void delete(String storedName) throws Exception {
        OSS oss = build();
        try {
            oss.deleteObject(bucket, storedName);
            log.info("阿里云OSS删除成功: {}", storedName);
        } finally {
            oss.shutdown();
        }
    }
}
