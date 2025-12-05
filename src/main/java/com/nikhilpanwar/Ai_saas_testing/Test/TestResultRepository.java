package com.nikhilpanwar.Ai_saas_testing.Test;

import com.nikhilpanwar.Ai_saas_testing.Test.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, String> {

    // 1. Fetch user specific logs
    List<TestResult> findByUserIdOrderByCreatedAtDesc(String userId);

    // =========================================================================
    //  STATS QUERIES (Updated to require userId)
    // =========================================================================

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.userId = :userId AND t.status = 'passed'")
    long countPassedTests(@Param("userId") String userId);

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.userId = :userId AND t.status = 'processing'")
    long countActiveTests(@Param("userId") String userId);

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.userId = :userId AND t.status = 'failed'")
    long countFailedTests(@Param("userId") String userId);

    // Note: Native query mein column name 'user_id' assume kiya gaya hai (Hibernate default)
    @Query(value = """
        SELECT 
            DATE(tr.created_at) AS date,
            COUNT(*) 
        FROM test_results tr
        WHERE tr.user_id = :userId 
        AND tr.created_at >= NOW() - INTERVAL '7 days'
        GROUP BY DATE(tr.created_at)
        ORDER BY DATE(tr.created_at)
    """, nativeQuery = true)
    List<Object[]> countTestsByDay(@Param("userId") String userId);


    // =========================================================================
    // COMPARISON QUERIES (Updated to require userId)
    // =========================================================================

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.userId = :userId AND t.executionTime >= :startDateTime AND t.executionTime <= :endDateTime")
    long countTestsBetween(
            @Param("userId") String userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.userId = :userId AND t.status = 'passed' AND t.executionTime >= :startDateTime AND t.executionTime <= :endDateTime")
    long countPassedTestsBetween(
            @Param("userId") String userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query("SELECT t.duration FROM TestResult t WHERE t.userId = :userId AND t.executionTime >= :startDateTime AND t.executionTime <= :endDateTime")
    List<String> findDurationsBetween(
            @Param("userId") String userId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

}