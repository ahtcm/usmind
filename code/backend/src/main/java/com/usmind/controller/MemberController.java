package com.usmind.controller;

import com.usmind.common.Result;
import com.usmind.common.SecurityUtil;
import com.usmind.service.MemberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping("/plans")
    public Result<List<Map<String, Object>>> plans() {
        return Result.ok(memberService.plans());
    }

    @PostMapping("/redeem")
    public Result<Map<String, Object>> redeem(@RequestBody Map<String, String> body) {
        return Result.ok(memberService.redeem(SecurityUtil.currentUserId(), body.get("code")));
    }

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        return Result.ok(memberService.memberInfo(SecurityUtil.currentUserId()));
    }
}
