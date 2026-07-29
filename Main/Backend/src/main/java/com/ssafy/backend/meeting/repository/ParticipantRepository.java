package com.ssafy.backend.meeting.repository;

import com.ssafy.backend.meeting.entity.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

}