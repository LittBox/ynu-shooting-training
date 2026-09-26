package com.ynu.shoting.repository;

import com.ynu.shoting.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from User e where e.id = :id")
    java.util.Optional<User> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);


    Optional<User> findByOpenid(String openid);

    boolean existsByOpenid(String openid);
}
