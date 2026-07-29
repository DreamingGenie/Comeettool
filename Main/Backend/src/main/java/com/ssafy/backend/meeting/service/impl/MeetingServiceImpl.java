package com.ssafy.backend.meeting.service.impl;

import org.springframework.stereotype.Service;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.meeting.service.MeetingService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;


/*
* MEET-06 회의 호스트 위임
*/