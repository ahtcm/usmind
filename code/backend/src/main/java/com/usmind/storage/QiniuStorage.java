package com.usmind.storage;

import com.qiniu.common.QiniuException;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QiniuStorage implements StorageStrategy {

    private final String accessKey;
    private final String secretKey;
    private final String bucket;
    private final String region;
    private final String domain;

    public QiniuStorage(String accessKey, String secretKey, String bucket,
                        String region, String domain) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.bucket = bucket;
        this.region = region;
        this.domain = domain;
    }

    @Override
    public String type() {
        return "qiniu";
    }

    private UploadManager buildManager() {
        Configuration cfg;
        if ("z0".equalsIgnoreCase(region)) cfg = new Configuration(Region.huadong());
        else if ("z1".equalsIgnoreCase(region)) cfg = new Configuration(Region.huabei());
        else if ("z2".equalsIgnoreCase(region)) cfg = new Configuration(Region.huanan());
        else if ("na0".equalsIgnoreCase(region)) cfg = new Configuration(Region.beimei());
        else if ("as0".equalsIgnoreCase(region)) cfg = new Configuration(Region.xinjiapo());
        else cfg = new Configuration(Region.autoRegion());
        return new UploadManager(cfg);
    }

    @Override
    public String upload(byte[] data, String storedName, String contentType) throws Exception {
        Auth auth = Auth.create(accessKey, secretKey);
        String token = auth.uploadToken(bucket, storedName, 3600L, null);
        UploadManager manager = buildManager();
        com.qiniu.http.Response r = manager.put(data, storedName, token, null, contentType, false);
        if (!r.isOK()) {
            throw new QiniuException(r);
        }
        log.info("七牛上传成功: {}", storedName);
        String base = (domain == null || domain.isEmpty()) ? "" : domain;
        if (!base.endsWith("/")) base = base + "/";
        return base + storedName;
    }

    @Override
    public void test() throws Exception {
        Auth auth = Auth.create(accessKey, secretKey);
        try {
            auth.uploadToken(bucket);
        } catch (Exception e) {
            throw new IllegalStateException("七牛凭证测试失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String storedName) throws Exception {
        Auth auth = Auth.create(accessKey, secretKey);
        com.qiniu.storage.BucketManager bm = new com.qiniu.storage.BucketManager(auth, new Configuration(Region.autoRegion()));
        com.qiniu.http.Response r = bm.delete(bucket, storedName);
        if (!r.isOK()) {
            log.warn("七牛删除未完全成功: status={}", r.statusCode);
        } else {
            log.info("七牛删除成功: {}", storedName);
        }
    }
}
