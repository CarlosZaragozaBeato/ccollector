package com.zensyra.collector.runner.health;

import com.zensyra.collector.core.sync.DataCollector;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;

@QuarkusTest
class JobRegistryHealthCheckTest {

    @Inject
    Instance<DataCollector> collectors;

    @Test
    void shouldBeUpWithRegisteredJobs() {
        int expectedJobs = collectors.stream()
                .mapToInt(collector -> collector.jobs().size())
                .sum();

        given()
            .when().get("/q/health/ready")
            .then()
            .statusCode(anyOf(is(200), is(503)))
            .body("checks.find { it.name == 'job-registry' }.status", is("UP"))
            .body("checks.find { it.name == 'job-registry' }.data.registeredJobs", is(expectedJobs));
    }
}
