package com.ynu.shoting;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
/** Runs the actual frontend request/domain adapters over HTTP against Spring and H2. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.profiles.active=integration", "spring.datasource.username=${test.database.user:sa}", "spring.datasource.password=${test.database.password:}", "spring.datasource.url=${test.database.url:jdbc:h2:mem:frontend-http;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}",
    "integration.clock=2026-09-25T00:00:00Z", "booking.sweeper-enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class FrontendHttpIntegrationTest {
    @LocalServerPort int port;
    @Test void actualFrontendAdaptersCompleteTheBusinessLoop() throws Exception {
        var output=Path.of("target","frontend-http-smoke.log").toAbsolutePath();
        var command=new ProcessBuilder("node", "scripts/integration-smoke.mjs").directory(Path.of("..","frontend").toFile())
            .redirectErrorStream(true).redirectOutput(output.toFile());
        command.environment().put("TEST_API_BASE_URL","http://127.0.0.1:"+port);
        var process=command.start();
        boolean done=process.waitFor(30,TimeUnit.SECONDS);
        if(!done)process.destroyForcibly();
        assertTrue(done,"Frontend HTTP smoke test timed out");
        assertEquals(0,process.exitValue(),()->"See "+output);
    }
}
