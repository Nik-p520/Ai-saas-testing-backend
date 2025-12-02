package com.nikhilpanwar.Ai_saas_testing.Test;

import com.nikhilpanwar.Ai_saas_testing.Test.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, String> {

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.status = 'passed'")
    long countPassedTests();

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.status = 'processing'")
    long countActiveTests();

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.status = 'failed'")
    long countFailedTests();

    @Query(value = """
    SELECT 
        DATE(tr.created_at) AS date,
        COUNT(*) 
    FROM test_results tr
    WHERE tr.created_at >= NOW() - INTERVAL '7 days'
    GROUP BY DATE(tr.created_at)
    ORDER BY DATE(tr.created_at)
""", nativeQuery = true)
    List<Object[]> countTestsByDay();


    // =========================================================================
    // COMPARISON QUERIES (FIXED)
    // =========================================================================

    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.executionTime >= :startDateTime AND t.executionTime <= :endDateTime")
    long countTestsBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    // 2. Count Passed Tests in range
    @Query("SELECT COUNT(t) FROM TestResult t WHERE t.status = 'passed' AND t.executionTime >= :startDateTime AND t.executionTime <= :endDateTime")
    long countPassedTestsBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    // 3. Fetch Durations in range (Must be done in Java, since 'duration' is a String)
    @Query("SELECT t.duration FROM TestResult t WHERE t.executionTime >= :startDateTime AND t.executionTime <= :endDateTime")
    List<String> findDurationsBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);
}
