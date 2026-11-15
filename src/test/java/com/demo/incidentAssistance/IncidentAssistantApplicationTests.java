package com.demo.incidentAssistance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Needs Postgres, network access for the embedding model, and API keys. "
		+ "Replaced by a Testcontainers integration test.")
class IncidentAssistantApplicationTests {

	@Test
	void contextLoads() {
	}

}
