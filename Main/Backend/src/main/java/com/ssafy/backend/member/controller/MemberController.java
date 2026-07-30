package com.ssafy.backend.member.controller;

import com.ssafy.backend.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

}