package com.team27.amazon.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.team27.amazon.user.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    //S1-F4
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM orders
                WHERE user_id = :userId
                  AND status IN ('PENDING', 'CONFIRMED', 'SHIPPED')
            )
            """, nativeQuery = true)
    boolean existsActiveOrdersByUserId(@Param("userId") Long userId);
}