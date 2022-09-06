package com.example.itmonster.repository;

import com.example.itmonster.domain.Follow;
import org.springframework.data.jpa.repository.JpaRepository;


public interface FollowRepository extends JpaRepository<Follow, Long> {

    Follow findByFollwingIdAndMeId (Long following, Long Me);
}
