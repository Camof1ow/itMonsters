package com.example.itsquad.repository;

import com.example.itsquad.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findById(Long id);

    Optional<Member> findByNickname(String nickname);

    Optional<Member> findByEmail(String email);

    Optional<Member> findBySocialId(String socialId);


    List<Member> findAllByNicknameContaining(String nickname);
}