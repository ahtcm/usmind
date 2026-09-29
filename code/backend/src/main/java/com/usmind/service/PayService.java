package com.usmind.service;

import java.util.Map;

/**
 * 微信支付（Native 扫码）：代码就绪、默认关闭（wechatPayEnabled=false），后台配置商户号后开启。
 */
public interface PayService {
    Map<String, Object> createWechatOrder(Long userId, Long planId);

    /** 支付回调处理，返回应答 XML/文本 */
    String handleWechatNotify(String body, Map<String, String> headers);
}
