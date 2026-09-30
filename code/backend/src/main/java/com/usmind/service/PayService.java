package com.usmind.service;

import java.util.Map;

/**
 * 微信支付（Native 扫码）：代码就绪、默认关闭（wechatPayEnabled=false），后台配置商户号后开启。
 */
public interface PayService {
    Map<String, Object> createWechatOrder(Long userId, Long planId);

    /** 微信 H5 支付下单（手机浏览器拉起微信，返回 mweb_url；默认关闭） */
    Map<String, Object> createWechatH5Order(Long userId, Long planId);

    /** 支付回调处理，返回应答 XML/文本 */
    String handleWechatNotify(String body, Map<String, String> headers);
}
