package com.ynu.shoting.repository;

import com.ynu.shoting.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByStudentNo(String studentNo);

    Optional<Profile> findByUserId(Long userId);
}
