package app.atrium.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logback appender (M3.5, 10 §6) — POSTs a Slack-incoming-webhook-shaped JSON
 * body ({@code {"text": "..."}}, so a real Slack webhook URL works with zero
 * adaptation) for every {@code ERROR}-level log line, debounced by a cooldown
 * so one error storm can't become a webhook storm. This is the app-level half
 * of "a triggered error alerts within 1 min" — the AWS-level half (a
 * CloudWatch metric filter on ERROR JSON lines → SNS → email, 10 §3) is real
 * Terraform but only fires once that infra is actually applied; both paths
 * can point at the same downstream, so they're complementary.
 *
 * <p>Instantiated directly by Logback's Joran config from {@code
 * logback-spring.xml}, NOT a Spring bean — logging initializes before the
 * Spring context does — so its config arrives via plain JavaBean setters
 * ({@code <webhookUrl>}/{@code <cooldownSeconds>} in the XML, themselves
 * filled from {@code atrium.alerting.*} via {@code <springProperty>}) rather
 * than {@code @Value}.
 *
 * <p>A blank {@code webhookUrl} (the local/test default — {@code
 * ATRIUM_ALERT_WEBHOOK_URL} unset) makes {@link #append} a complete no-op.
 * The POST itself runs on a small background executor, never the calling
 * thread — a slow or unreachable webhook must never slow down request
 * handling or a business transaction that happened to log an ERROR.
 */
public class AlertingAppender extends AppenderBase<ILoggingEvent> {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    private String webhookUrl;
    private int cooldownSeconds = 30;
    private final AtomicLong lastSentAtMillis = new AtomicLong(0);
    private ExecutorService executor;

    /** Joran setter — populated from {@code <webhookUrl>} in the XML. */
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    /** Joran setter — populated from {@code <cooldownSeconds>} in the XML. */
    public void setCooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "alerting-appender");
            t.setDaemon(true);
            return t;
        });
        super.start();
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (webhookUrl == null || webhookUrl.isBlank() || event.getLevel() != Level.ERROR) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastSentAtMillis.get();
        if (now - last < cooldownSeconds * 1000L) {
            return;
        }
        if (!lastSentAtMillis.compareAndSet(last, now)) {
            return; // another thread just won the cooldown race
        }
        String text = event.getLoggerName() + " ERROR: " + event.getFormattedMessage();
        executor.submit(() -> send(text));
    }

    private void send(String text) {
        try {
            String body = JSON.writeValueAsString(Map.of("text", text));
            HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            addError("Failed to send error alert webhook", e);
        }
    }
}
