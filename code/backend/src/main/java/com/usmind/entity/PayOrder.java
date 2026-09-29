package com.usmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pay_order")
public class PayOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String outTradeNo;
    private Long userId;
    private Long planId;
    private BigDecimal amount;
    private String channel;
    private String status;
    private String transactionId;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
