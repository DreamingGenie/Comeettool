package com.ssafy.backend.meeting.repository;

import com.interface ssafy.backend.meeting.entity.MeetingRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MeetingRoomRepository extends JpaRepository<MeetingRoom, Long> {

}