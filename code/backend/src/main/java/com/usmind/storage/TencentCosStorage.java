package com.usmind.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;

@Slf4j
public class TencentCosStorage implements StorageStrategy {

    private final String secretId;
    private final String secretKey;
    private final String bucket;
    private final String region;
    private final String domain;

    public TencentCosStorage(String accessKey, String secretKey, String bucket,
                             String region, String domain) {
        this.secretId = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.region = region;
        this.domain = domain;
    }

    @Override
    public String type() {
        return "tencent";
    }

    private COSClient build() {
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig config = new ClientConfig(new Region(region));
        config.setHttpProtocol(HttpProtocol.https);
        return new COSClient(cred, config);
    }

    @Override
    public String upload(byte[] data, String storedName, String contentType) throws Exception {
        COSClient client = build();
        try {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(data.length);
            if (contentType != null) meta.setContentType(contentType);
            PutObjectRequest req = new PutObjectRequest(bucket, storedName,
                    new ByteArrayInputStream(data), meta);
            client.putObject(req);
            log.info("腾讯COS上传成功: {}", storedName);
            String base = (domain == null || domain.isEmpty())
                    ? ("https://" + bucket + ".cos." + region + ".myqcloud.com/") : domain;
            if (!base.endsWith("/")) base = base + "/";
            return base + storedName;
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void test() throws Exception {
        COSClient client = build();
        try {
            client.getBucketLocation(bucket);
        } catch (Exception e) {
            throw new IllegalStateException("COS连接测试失败: " + e.getMessage(), e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void delete(String storedName) throws Exception {
        COSClient client = build();
        try {
            client.deleteObject(bucket, storedName);
            log.info("腾讯COS删除成功: {}", storedName);
        } finally {
            client.shutdown();
        }
    }
}
