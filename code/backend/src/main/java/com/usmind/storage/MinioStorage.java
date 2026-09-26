package com.usmind.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;

@Slf4j
public class MinioStorage implements StorageStrategy {

    private final String endpoint;
    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final String domain;

    public MinioStorage(String endpoint, String accessKey, String secretKey,
                        String bucket, String domain) {
        this.endpoint = endpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.domain = domain;
    }

    @Override
    public String type() {
        return "minio";
    }

    private MinioClient build() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Override
    public String upload(byte[] data, String storedName, String contentType) throws Exception {
        MinioClient client = build();
        client.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(storedName)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType(contentType == null ? "application/octet-stream" : contentType)
                .build());
        log.info("MinIO上传成功: {}", storedName);
        String base = (domain == null || domain.isEmpty()) ? endpoint : domain;
        if (!base.endsWith("/")) base = base + "/";
        return base + bucket + "/" + storedName;
    }

    @Override
    public void test() throws Exception {
        MinioClient client = build();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            throw new IllegalStateException("bucket 不存在: " + bucket);
        }
    }

    @Override
    public void delete(String storedName) throws Exception {
        MinioClient client = build();
        client.removeObject(io.minio.RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(storedName)
                .build());
        log.info("MinIO删除成功: {}", storedName);
    }
}
