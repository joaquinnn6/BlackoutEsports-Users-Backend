package cl.duocuc.blackout.users;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties={"app.rabbit.enabled=true", "spring.rabbitmq.listener.simple.auto-startup=false",
    "academy.mail-consumer-enabled=false", "spring.datasource.url=jdbc:h2:mem:amqp-context;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Import(TestTokens.class)
class AcademyMessagingContextTests {
    @Test void laConfiguracionDeListenersArrancaSinDependenciasCirculares() {}
}
