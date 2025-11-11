package com.nikhilpanwar.Ai_saas_testing.Test;

import com.nikhilpanwar.Ai_saas_testing.Test.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TestResultRepository extends JpaRepository<TestResult, String> {


}
