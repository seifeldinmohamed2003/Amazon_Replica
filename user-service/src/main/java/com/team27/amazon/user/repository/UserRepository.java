package com.team27.amazon.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.team27.amazon.user.model.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // S1-F1
    @Query(value = """
        SELECT * FROM users
        WHERE (:name IS NULL OR LOWER(name) LIKE LOWER(CONCAT('%', :name, '%')))
          AND (:email IS NULL OR LOWER(email) LIKE LOWER(CONCAT('%', :email, '%')))
          AND (:role IS NULL OR role = :role)
        """, nativeQuery = true)
    List<User> searchUsers(
            @Param("name") String name,
            @Param("email") String email,
            @Param("role") String role
    );

    // S1-F5
    @Query(value = """
            SELECT *
            FROM users
            WHERE preferences ->> :key = :value
            """, nativeQuery = true)
    List<User> findUsersByPreference(@Param("key") String key, @Param("value") String value);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    Optional<User> findByEmail(String email);
}