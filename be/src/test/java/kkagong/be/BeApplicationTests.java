package kkagong.be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.show-sql=false",
		"spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class BeApplicationTests {

	@Test
	void contextLoads() {
	}

}
