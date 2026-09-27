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


    @org.springframework.data.jpa.repository.Query("select u from User u join fetch u.profile p where u.profileStatus = 'completed' and (locate(lower(:search),lower(p.realName)) > 0 or locate(:search,p.studentNo) > 0) order by p.realName,u.id")
    org.springframework.data.domain.Slice<User> searchMembers(@org.springframework.data.repository.query.Param("search") String search, org.springframework.data.domain.Pageable pageable);

    Optional<User> findByOpenid(String openid);

    boolean existsByOpenid(String openid);
}
