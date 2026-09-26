package com.usmind.storage;

public interface StorageStrategy {

    /** 存储类型: local/aliyun/tencent/qiniu/minio */
    String type();

    /**
     * 上传文件字节
     * @param data 文件字节
     * @param storedName 存储后的对象名（含目录前缀）
     * @param contentType MIME
     * @return 可访问的 URL（相对或绝对）
     */
    String upload(byte[] data, String storedName, String contentType) throws Exception;

    /** 连接/配置自检，失败抛异常 */
    void test() throws Exception;

    /** 删除存储对象（默认空实现，各存储按需覆盖） */
    default void delete(String storedName) throws Exception {
    }
}
