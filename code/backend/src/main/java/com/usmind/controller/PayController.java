package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.service.PayService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pay")
public class PayController {

    private final PayService payService;

    public PayController(PayService payService) {
        this.payService = payService;
    }

    /** 微信支付 Native 下单（需登录，默认关闭，后台开启后生效） */
    @PostMapping("/wechat/create")
    public Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Long planId = body.get("planId") == null ? null : Long.valueOf(String.valueOf(body.get("planId")));
        return Result.ok(payService.createWechatOrder(SecurityUtil.currentUserId(), planId));
    }

    /** 微信 H5 支付下单（手机浏览器拉起微信，需登录，默认关闭） */
    @PostMapping("/wechat/create-h5")
    public Result<Map<String, Object>> createH5(@RequestBody Map<String, Object> body,
                                                jakarta.servlet.http.HttpServletRequest request) {
        Long planId = body.get("planId") == null ? null : Long.valueOf(String.valueOf(body.get("planId")));
        return Result.ok(payService.createWechatH5Order(SecurityUtil.currentUserId(), planId));
    }

    /** 微信支付回调（放行，默认关闭时不处理；开启后严格验签+解密） */
    @PostMapping("/wechat/notify")
    public String notify(@RequestBody String body,
                         @RequestHeader(value = "Wechatpay-Timestamp", required = false) String ts,
                         @RequestHeader(value = "Wechatpay-Nonce", required = false) String nonce,
                         @RequestHeader(value = "Wechatpay-Signature", required = false) String sig,
                         @RequestHeader(value = "Wechatpay-Serial", required = false) String serial) {
        return payService.handleWechatNotify(body, Map.of(
                "timestamp", ts == null ? "" : ts,
                "nonce", nonce == null ? "" : nonce,
                "signature", sig == null ? "" : sig,
                "serial", serial == null ? "" : serial));
    }
}
