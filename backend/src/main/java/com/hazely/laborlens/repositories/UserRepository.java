package com.hazely.laborlens.repositories;

import com.hazely.laborlens.entities.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

/** Serialize credential issuance and revocation against the same user row. */
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> lockEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockId(@Param("id") long id);
}
