package com.team27.amazon.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.team27.amazon.user.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}