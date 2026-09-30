package com.usmind.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usmind.common.BusinessException;
import com.usmind.entity.MemberPlan;
import com.usmind.entity.PayOrder;
import com.usmind.mapper.MemberPlanMapper;
import com.usmind.mapper.PayOrderMapper;
import com.usmind.service.MemberService;
import com.usmind.service.PayService;
import com.usmind.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class PayServiceImpl implements PayService {

    private final MemberPlanMapper planMapper;
    private final PayOrderMapper orderMapper;
    private final SettingsService settingsService;
    private final MemberService memberService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PayServiceImpl(MemberPlanMapper planMapper, PayOrderMapper orderMapper,
                          SettingsService settingsService, MemberService memberService) {
        this.planMapper = planMapper;
        this.orderMapper = orderMapper;
        this.settingsService = settingsService;
        this.memberService = memberService;
    }

    @Override
    public Map<String, Object> createWechatOrder(Long userId, Long planId) {
        return createWechatOrderInternal(userId, planId, "native", null);
    }

    @Override
    public Map<String, Object> createWechatH5Order(Long userId, Long planId) {
        return createWechatOrderInternal(userId, planId, "h5", null);
    }

    /** 统一微信 v3 下单：type=native 返回 code_url（PC 扫码）；type=h5 返回 mweb_url（手机拉起微信） */
    private Map<String, Object> createWechatOrderInternal(Long userId, Long planId, String type, String clientIp) {
        // 微信支付默认关闭，后台开启并配置商户参数后生效
        if (settingsService.getInt("wechatPayEnabled", 0) != 1) {
            throw new BusinessException(400, "微信支付未开启");
        }
        String mchId = settingsService.get("wechatMchId", "");
        String appId = settingsService.get("wechatAppId", "");
        String apiKey = settingsService.get("wechatApiKey", "");
        String privateKey = settingsService.get("wechatPrivateKey", "");
        String notifyUrl = settingsService.get("wechatNotifyUrl", "");
        if (mchId.isBlank() || appId.isBlank() || apiKey.isBlank() || privateKey.isBlank() || notifyUrl.isBlank()) {
            throw new BusinessException(400, "微信支付参数未配置完整，请在管理后台配置");
        }
        MemberPlan plan = planMapper.selectById(planId);
        if (plan == null || plan.getEnabled() == null || plan.getEnabled() != 1) {
            throw new BusinessException(400, "会员套餐不存在或已下架");
        }
        String outTradeNo = "USM" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        PayOrder order = new PayOrder();
        order.setOutTradeNo(outTradeNo);
        order.setUserId(userId);
        order.setPlanId(planId);
        order.setAmount(plan.getPrice());
        order.setChannel("wechat");
        order.setStatus("created");
        order.setCreatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        String path = "/v3/pay/transactions/native";
        if ("h5".equals(type)) path = "/v3/pay/transactions/h5";
        Map<String, Object> body = new HashMap<>();
        body.put("appid", appId);
        body.put("mchid", mchId);
        body.put("description", plan.getName());
        body.put("out_trade_no", outTradeNo);
        body.put("notify_url", notifyUrl);
        Map<String, Object> amount = new HashMap<>();
        amount.put("total", plan.getPrice().multiply(new BigDecimal("100")).intValue());
        amount.put("currency", "CNY");
        body.put("amount", amount);
        if ("h5".equals(type)) {
            Map<String, Object> h5Info = new HashMap<>();
            h5Info.put("type", "Wap");
            Map<String, Object> sceneInfo = new HashMap<>();
            sceneInfo.put("h5_info", h5Info);
            sceneInfo.put("payer_client_ip", clientIp == null ? "127.0.0.1" : clientIp);
            body.put("scene_info", sceneInfo);
        }
        try {
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String json = objectMapper.writeValueAsString(body);
            String message = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + json + "\n";
            String signature = sign(message, privateKey);
            String auth = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + mchId + "\",nonce_str=\"" + nonce
                    + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + apiKey + "\",signature=\"" + signature + "\"";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mch.weixin.qq.com" + path))
                    .header("Authorization", auth)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "USMIND/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("微信下单失败: {} {}", resp.statusCode(), resp.body());
                orderMapper.deleteById(order.getId());
                throw new BusinessException(500, "微信下单失败: " + resp.body());
            }
            Map<String, Object> r = objectMapper.readValue(resp.body(), Map.class);
            Map<String, Object> out = new HashMap<>();
            out.put("orderId", order.getId());
            out.put("outTradeNo", outTradeNo);
            out.put("amount", plan.getPrice());
            if ("h5".equals(type)) {
                out.put("mwebUrl", r.get("mweb_url"));
            } else {
                out.put("codeUrl", r.get("code_url"));
            }
            return out;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信下单异常", e);
            throw new BusinessException(500, "微信下单异常: " + e.getMessage());
        }
    }

    private String sign(String message, String privateKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PrivateKey privateKey = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    @Override
    public String handleWechatNotify(String body, Map<String, String> headers) {
        // 默认关闭时不接受回调
        if (settingsService.getInt("wechatPayEnabled", 0) != 1) {
            return fail("支付未开启");
        }
        // ===== 微信支付 v3 回调：严格验签 + AES-GCM 解密 + 金额/商户校验 =====
        try {
            // 1. 验签
            String timestamp = headers.getOrDefault("timestamp", "");
            String nonce = headers.getOrDefault("nonce", "");
            String signature = headers.getOrDefault("signature", "");
            String serial = headers.getOrDefault("serial", "");
            String platformCert = settingsService.get("wechatPlatformCert", "");
            if (timestamp.isBlank() || nonce.isBlank() || signature.isBlank() || platformCert.isBlank()) {
                log.warn("微信回调验签失败：缺少时间戳/随机串/签名/平台证书配置");
                return fail("验签失败");
            }
            long ts;
            try {
                ts = Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                return fail("验签失败");
            }
            if (Math.abs(System.currentTimeMillis() / 1000 - ts) > 300) {
                log.warn("微信回调时间戳超时: ts={}", timestamp);
                return fail("验签失败");
            }
            String message = timestamp + "\n" + nonce + "\n" + body + "\n";
            if (!verifyPlatformSignature(message, signature, platformCert)) {
                log.warn("微信回调签名校验失败 serial={}", serial);
                return fail("验签失败");
            }

            // 2. 解密 resource（AES-256-GCM，key = APIv3 密钥）
            Map<String, Object> m = objectMapper.readValue(body, Map.class);
            Object resourceObj = m.get("resource");
            if (!(resourceObj instanceof Map)) {
                return fail("回调数据格式错误");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> resource = (Map<String, Object>) resourceObj;
            String apiV3Key = settingsService.get("wechatApiV3Key", "");
            if (apiV3Key.isBlank()) {
                log.warn("微信回调缺少 APIv3 密钥配置");
                return fail("验签失败");
            }
            String decrypted = decryptResource(resource, apiV3Key);
            if (decrypted == null) {
                log.warn("微信回调 resource 解密失败");
                return fail("解密失败");
            }
            Map<String, Object> tx = objectMapper.readValue(decrypted, Map.class);

            // 3. 业务校验：商户号、订单号、金额
            String mchId = settingsService.get("wechatMchId", "");
            String txMchId = String.valueOf(tx.getOrDefault("mchid", ""));
            if (!mchId.equals(txMchId)) {
                log.warn("微信回调商户号不匹配: expect={} got={}", mchId, txMchId);
                return fail("商户号不匹配");
            }
            String outTradeNo = String.valueOf(tx.getOrDefault("out_trade_no", ""));
            Object amountObj = tx.get("amount");
            if (outTradeNo.isBlank() || !(amountObj instanceof Map)) {
                return fail("回调数据不完整");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> amount = (Map<String, Object>) amountObj;
            long totalFen = Long.parseLong(String.valueOf(amount.getOrDefault("total", "-1")));

            PayOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                    .eq(PayOrder::getOutTradeNo, outTradeNo).last("LIMIT 1"));
            if (order == null) {
                log.warn("微信回调订单不存在: {}", outTradeNo);
                return fail("订单不存在");
            }
            long expectFen = order.getAmount().multiply(new BigDecimal("100")).longValue();
            if (totalFen != expectFen) {
                log.warn("微信回调金额不匹配: expect={} got={}", expectFen, totalFen);
                return fail("金额不匹配");
            }
            if ("created".equals(order.getStatus())) {
                order.setStatus("paid");
                order.setTransactionId(String.valueOf(tx.getOrDefault("transaction_id", "")));
                order.setPaidAt(LocalDateTime.now());
                orderMapper.updateById(order);
                MemberPlan plan = planMapper.selectById(order.getPlanId());
                int days = plan != null && plan.getDays() != null ? plan.getDays() : 30;
                int level = plan != null && plan.getLevel() != null ? plan.getLevel() : 1;
                memberService.grant(order.getUserId(), level, days);
                log.info("微信回调验签通过，开通会员: userId={} level={} days={}", order.getUserId(), level, days);
            }
            return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
        } catch (Exception e) {
            log.error("微信回调处理失败", e);
            return fail("处理失败");
        }
    }

    /** 使用微信支付平台证书公钥验签 */
    private boolean verifyPlatformSignature(String message, String signature, String platformCert) {
        try {
            byte[] certBytes = parsePem(platformCert);
            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(certBytes));
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(cert.getPublicKey());
            sig.update(message.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            log.warn("微信平台证书验签异常: {}", e.getMessage());
            return false;
        }
    }

    /** 解密微信支付回调 resource（AES-256-GCM） */
    private String decryptResource(Map<String, Object> resource, String apiV3Key) {
        try {
            String algorithm = String.valueOf(resource.getOrDefault("algorithm", ""));
            if (!"AEAD_AES_256_GCM".equals(algorithm)) {
                log.warn("不支持的加密算法: {}", algorithm);
                return null;
            }
            byte[] key = apiV3Key.getBytes(StandardCharsets.UTF_8);
            if (key.length != 32) {
                key = java.util.Arrays.copyOfRange(java.util.Arrays.copyOf(key, 32), 0, 32);
            }
            byte[] nonce = Base64.getDecoder().decode(String.valueOf(resource.get("nonce")));
            byte[] aad = String.valueOf(resource.getOrDefault("associated_data", "")).getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = Base64.getDecoder().decode(String.valueOf(resource.get("ciphertext")));
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, nonce);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), spec);
            cipher.updateAAD(aad);
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("微信回调解密失败: {}", e.getMessage());
            return null;
        }
    }

    private byte[] parsePem(String pem) {
        String cleaned = pem.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }

    private String fail(String msg) {
        return "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[" + msg + "]]></return_msg></xml>";
    }
}
