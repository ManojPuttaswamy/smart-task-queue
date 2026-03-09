package com.smartqueue.repository;

import com.smartqueue.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Standard Spring Data repository for User.
 *
 * findByUsername is the key method — used during login to look up
 * the user by their login name and validate their password.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);
}