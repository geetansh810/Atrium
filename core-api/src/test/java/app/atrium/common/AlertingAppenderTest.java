package app.atrium.common;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Standalone unit test — {@link AlertingAppender} is a plain Logback
 * appender (Joran-instantiated from {@code logback-spring.xml}, not a Spring
 * bean), so this attaches it directly to a throwaway logger rather than
 * spinning up an {@code IntegrationTestBase} context.
 */
class AlertingAppenderTest {

    private HttpServer server;
    private final List<String> receivedBodies = new CopyOnWriteArrayList<>();
    private String webhookUrl;
    private Logger logger;
    private AlertingAppender appender;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedBodies.add(new String(body, StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        webhookUrl = "http://localhost:" + server.getAddress().getPort() + "/hook";

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = context.getLogger("AlertingAppenderTest." + System.nanoTime());
        logger.setLevel(Level.ALL);
        logger.setAdditive(false);
    }

    @AfterEach
    void tearDown() {
        if (appender != null) {
            appender.stop();
        }
        server.stop(0);
    }

    private AlertingAppender attach(String url, int cooldownSeconds) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        AlertingAppender a = new AlertingAppender();
        a.setContext(context);
        a.setWebhookUrl(url);
        a.setCooldownSeconds(cooldownSeconds);
        a.start();
        logger.addAppender(a);
        this.appender = a;
        return a;
    }

    private void waitUntil(int expectedCount, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (receivedBodies.size() < expectedCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Test
    void errorLevelLogTriggersAWebhookPost() throws InterruptedException {
        attach(webhookUrl, 30);

        logger.error("something broke");

        waitUntil(1, 2000);
        assertThat(receivedBodies).hasSize(1);
        assertThat(receivedBodies.get(0)).contains("something broke").contains("\"text\"");
    }

    @Test
    void nonErrorLevelsNeverPost() throws InterruptedException {
        attach(webhookUrl, 30);

        logger.warn("just a warning");
        logger.info("just info");

        Thread.sleep(300);
        assertThat(receivedBodies).isEmpty();
    }

    @Test
    void blankWebhookUrlIsANoOp() throws InterruptedException {
        attach("", 30);

        logger.error("nobody is listening");

        Thread.sleep(300);
        assertThat(receivedBodies).isEmpty();
    }

    @Test
    void cooldownDropsASecondErrorButAllowsTheNextWindow() throws InterruptedException {
        attach(webhookUrl, 1);

        logger.error("first");
        waitUntil(1, 2000);
        assertThat(receivedBodies).as("first error posts immediately").hasSize(1);

        logger.error("second, inside the cooldown");
        Thread.sleep(300);
        assertThat(receivedBodies).as("second error dropped, still within the 1s cooldown").hasSize(1);

        Thread.sleep(900); // past the 1s cooldown window
        logger.error("third, after cooldown");
        waitUntil(2, 2000);
        assertThat(receivedBodies).as("third error posts once cooldown has elapsed").hasSize(2);
    }
}
