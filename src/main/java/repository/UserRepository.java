package com.theguy.app.repository;

import com.theguy.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    Optional<User> findByPhoneNumber(String phoneNumber);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByPhoneNumber(String phoneNumber);
    
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.phoneNumber = :phoneNumber AND u.isVerified = true")
    Optional<User> findVerifiedByPhoneNumber(@Param("phoneNumber") String phoneNumber);
    
    @Query("SELECT COUNT(j) FROM Job j WHERE j.customer.id = :userId AND j.status = 'COMPLETED'")
    Long countCompletedJobsByCustomer(@Param("userId") UUID userId);
}