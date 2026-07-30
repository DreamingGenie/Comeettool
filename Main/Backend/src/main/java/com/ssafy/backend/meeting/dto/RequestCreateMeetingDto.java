package com.ssafy.backend.meeting.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RequestCreateMeetingDto(

        @NotBlank
        @Size(max=250)
        String meetingRoomName
){
}
