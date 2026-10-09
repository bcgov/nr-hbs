package ca.bc.gov.nrs.hbs.api.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Outbound email for HBS — the replacement for the legacy log4j
 * {@code HbsSMTPAppender} (batch-failure alerts to FORHVAP.HBSSUPRT) and the
 * statement-delivery "your statements are ready" notices sent by job B2065.
 *
 * <p>Bodies come from {@code classpath:notification/template/*.txt} via
 * {@link EmailTemplateRenderer}. Honours the {@code hbs.mail.send-enabled}
 * kill-switch: when off, the message is rendered and logged but not sent.
 */
@Service
@Slf4j
public class EmailNotificationService {

  private final JavaMailSender mailSender;
  private final EmailTemplateRenderer renderer;
  private final String from;
  private final String support;
  private final boolean sendEnabled;

  public EmailNotificationService(
      JavaMailSender mailSender,
      EmailTemplateRenderer renderer,
      @Value("${hbs.mail.from}") String from,
      @Value("${hbs.mail.support}") String support,
      @Value("${hbs.mail.send-enabled:true}") boolean sendEnabled) {
    this.mailSender = mailSender;
    this.renderer = renderer;
    this.from = from;
    this.support = support;
    this.sendEnabled = sendEnabled;
  }

  /** Render {@code template} with {@code values} and send it to {@code to}. */
  public void send(String to, String subject, String template, Map<String, String> values) {
    String body = renderer.render(template, values);
    if (!sendEnabled) {
      log.info("Email sending disabled — would have sent '{}' to {}", subject, to);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    try {
      mailSender.send(message);
    } catch (MailException ex) {
      // Email is best-effort: a relay outage must never fail the batch or
      // request that triggered it.
      log.error("Failed to send '{}' to {}", subject, to, ex);
    }
  }

  /** Batch failure alert to the HBS support mailbox (legacy "Batch Failure - <job>"). */
  public void sendBatchFailure(String jobName, String detail) {
    send(support, "Batch Failure - " + jobName, "batch_failure_email",
        Map.of("jobName", jobName, "detail", detail == null ? "" : detail));
  }
}
