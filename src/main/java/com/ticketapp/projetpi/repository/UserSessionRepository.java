package com.ticketapp.projetpi.repository;

import com.ticketapp.projetpi.entity.User;
import com.ticketapp.projetpi.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    List<UserSession> findByUserOrderByLoginAtDesc(User user);
    Optional<UserSession> findByTokenJti(String tokenJti);
    void deleteByTokenJti(String tokenJti);
}
