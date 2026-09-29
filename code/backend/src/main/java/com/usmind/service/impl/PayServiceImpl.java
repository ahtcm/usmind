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

        // 微信支付 v3 Native 下单
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
        try {
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String json = objectMapper.writeValueAsString(body);
            String message = "POST\n/v3/pay/transactions/native\n" + timestamp + "\n" + nonce + "\n" + json + "\n";
            String signature = sign(message, privateKey);
            String auth = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + mchId + "\",nonce_str=\"" + nonce
                    + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + apiKey + "\",signature=\"" + signature + "\"";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mch.weixin.qq.com/v3/pay/transactions/native"))
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
            out.put("codeUrl", r.get("code_url"));
            out.put("amount", plan.getPrice());
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
        // 【生产环境必须】使用微信支付平台证书验签 + AES-GCM 解密 resource 字段，再校验金额/商户号。
        // 当前为可运行的最小闭环：解析出 out_trade_no 后查询订单并开通会员，方便本地联调；上线前请补齐官方验签。
        try {
            Map<String, Object> m = objectMapper.readValue(body, Map.class);
            String resource = String.valueOf(m.getOrDefault("resource", "{}"));
            String outTradeNo = extractOutTradeNo(resource);
            PayOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                    .eq(PayOrder::getOutTradeNo, outTradeNo).last("LIMIT 1"));
            if (order != null && "created".equals(order.getStatus())) {
                order.setStatus("paid");
                order.setTransactionId(String.valueOf(System.currentTimeMillis()));
                order.setPaidAt(LocalDateTime.now());
                orderMapper.updateById(order);
                MemberPlan plan = planMapper.selectById(order.getPlanId());
                int days = plan != null && plan.getDays() != null ? plan.getDays() : 30;
                int level = plan != null && plan.getLevel() != null ? plan.getLevel() : 1;
                memberService.grant(order.getUserId(), level, days);
                log.info("支付回调开通会员: userId={} level={} days={}", order.getUserId(), level, days);
            }
            return "<xml><return_code><![CDATA[SUCCESS]]></return_code><return_msg><![CDATA[OK]]></return_msg></xml>";
        } catch (Exception e) {
            log.error("微信回调处理失败", e);
            return fail("处理失败");
        }
    }

    private String extractOutTradeNo(String resource) {
        // resource 为密文时需解密；此处兼容明文/测试场景，取 out_trade_no 字段
        int i = resource.indexOf("out_trade_no");
        if (i < 0) return "";
        String sub = resource.substring(i + 13);
        int j = sub.indexOf('"');
        return sub.substring(0, j < 0 ? sub.length() : j).replace("\\", "");
    }

    private String fail(String msg) {
        return "<xml><return_code><![CDATA[FAIL]]></return_code><return_msg><![CDATA[" + msg + "]]></return_msg></xml>";
    }
}
