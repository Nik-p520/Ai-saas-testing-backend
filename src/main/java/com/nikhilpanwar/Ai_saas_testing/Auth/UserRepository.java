package com.nikhilpanwar.Ai_saas_testing.Auth;

import com.nikhilpanwar.Ai_saas_testing.Auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // Function ka naam change kiya 'findByAuth0Id' se 'findByFirebaseUid'
    Optional<User> findByFirebaseUid(String firebaseUid);
}
