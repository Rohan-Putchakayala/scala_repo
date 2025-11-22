package services

import play.api.{Configuration, Logging}

import java.util.Properties
import javax.inject.{Inject, Singleton}
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeMessage}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class EmailService @Inject()(config: Configuration)(implicit ec: ExecutionContext) extends Logging {

  private val smtpHost: String = config.getOptional[String]("email.smtp.host").getOrElse("smtp.gmail.com")
  private val smtpPort: String = config.getOptional[String]("email.smtp.port").getOrElse("587")
  private val smtpUser: String = config.getOptional[String]("email.smtp.user").getOrElse("").trim
  private val smtpPassword: String = config.getOptional[String]("email.smtp.password").getOrElse("").trim
  private val fromEmail: String = config.getOptional[String]("email.from").getOrElse(smtpUser).trim

  // Validate configuration but do not fail application startup; log warnings instead
  if (smtpUser.isEmpty || smtpPassword.isEmpty) {
    logger.warn("EmailService configuration: SMTP user/password missing. Emails will fail until configured.")
  }

  if (fromEmail.isEmpty) {
    logger.warn("EmailService configuration: email.from missing. Using smtp.user if available.")
  }

  if (fromEmail.nonEmpty && smtpUser.nonEmpty && !fromEmail.equalsIgnoreCase(smtpUser)) {
    logger.warn(s"EmailService configuration: email.from ($fromEmail) differs from email.smtp.user ($smtpUser). Ensure they match for Gmail.")
  }

  // Configure JavaMail / Gmail SMTP properties
  private val props = new Properties()
  props.put("mail.smtp.auth", "true")
  props.put("mail.smtp.starttls.enable", "true")
  props.put("mail.smtp.starttls.required", "true")
  props.put("mail.smtp.host", smtpHost)
  props.put("mail.smtp.port", smtpPort)
  props.put("mail.smtp.ssl.trust", smtpHost)

  // Timeouts (in milliseconds)
  private val connectionTimeoutMs: String = config.getOptional[Int]("email.smtp.connectionTimeoutMs").getOrElse(10000).toString
  private val readTimeoutMs: String        = config.getOptional[Int]("email.smtp.readTimeoutMs").getOrElse(10000).toString
  private val writeTimeoutMs: String       = config.getOptional[Int]("email.smtp.writeTimeoutMs").getOrElse(10000).toString

  props.put("mail.smtp.connectiontimeout", connectionTimeoutMs)
  props.put("mail.smtp.timeout", readTimeoutMs)
  props.put("mail.smtp.writetimeout", writeTimeoutMs)

  // Debug logging (do not log raw password)
  logger.debug(s"EmailService SMTP config loaded: host=$smtpHost, port=$smtpPort, user=$smtpUser, from=$fromEmail, passwordSet=${smtpPassword.nonEmpty}, connectionTimeoutMs=$connectionTimeoutMs, readTimeoutMs=$readTimeoutMs, writeTimeoutMs=$writeTimeoutMs")

  private def getSession: Session = {
    Session.getInstance(props, new Authenticator() {
      override protected def getPasswordAuthentication: PasswordAuthentication = {
        new PasswordAuthentication(smtpUser, smtpPassword)
      }
    })
  }

  def sendEmail(to: String, subject: String, body: String, isHtml: Boolean = false): Future[Boolean] = Future {
    Try {
      val session = getSession
      val message = new MimeMessage(session)
      
      message.setFrom(new InternetAddress(fromEmail))
      message.setRecipients(Message.RecipientType.TO, to)
      message.setSubject(subject)
      
      if (isHtml) {
        message.setContent(body, "text/html; charset=utf-8")
      } else {
        message.setText(body)
      }
      
      logger.info(s"[EmailService] Sending email - FROM: $fromEmail, TO: $to, Subject: $subject")
      Transport.send(message)
      true
    } match {
      case Success(_) =>
        logger.info(s"[EmailService] Email sent successfully - FROM: $fromEmail, TO: $to, Subject: '$subject'")
        true
      case Failure(ex) =>
        logger.error(s"[EmailService] Failed to send email - FROM: $fromEmail, TO: $to, Subject: '$subject', Error: ${ex.getMessage}", ex)
        false
    }
  }

  def sendHostNotification(hostEmail: String, visitorName: String, visitorEmail: String, 
                          visitorPhone: String, purposeOfVisit: String, checkInTime: String): Future[Boolean] = {
    val subject = s"Visitor Arrival Notification: $visitorName"
    val body = s"""
      |<html>
      |<body>
      |  <h2>Visitor Arrival Notification</h2>
      |  <p>Hello,</p>
      |  <p>Your visitor has arrived:</p>
      |  <ul>
      |    <li><strong>Name:</strong> $visitorName</li>
      |    <li><strong>Email:</strong> $visitorEmail</li>
      |    <li><strong>Phone:</strong> $visitorPhone</li>
      |    <li><strong>Purpose:</strong> $purposeOfVisit</li>
      |    <li><strong>Check-in Time:</strong> $checkInTime</li>
      |  </ul>
      |  <p>Please proceed to the reception to meet your visitor.</p>
      |  <br/>
      |  <p>Best regards,<br/>Reception Team</p>
      |</body>
      |</html>
    """.stripMargin
    
    sendEmail(hostEmail, subject, body, isHtml = true)
  }

  def sendITWiFiCredentials(visitorEmail: String, visitorName: String, wifiSSID: String = "Guest-WiFi", 
                           wifiPassword: String = "Guest@123"): Future[Boolean] = {
    val subject = "Wi-Fi Access Credentials"
    val body = s"""
      |<html>
      |<body>
      |  <h2>Welcome to Our Office!</h2>
      |  <p>Dear $visitorName,</p>
      |  <p>Your visitor Wi-Fi access has been prepared. Please use the following credentials:</p>
      |  <div style="background-color: #f4f4f4; padding: 15px; border-radius: 5px; margin: 15px 0;">
      |    <p><strong>Network (SSID):</strong> $wifiSSID</p>
      |    <p><strong>Password:</strong> $wifiPassword</p>
      |  </div>
      |  <p><strong>Note:</strong> These credentials are valid for today only.</p>
      |  <br/>
      |  <p>If you need any assistance, please contact IT Support.</p>
      |  <br/>
      |  <p>Best regards,<br/>IT Support Team</p>
      |</body>
      |</html>
    """.stripMargin
    
    sendEmail(visitorEmail, subject, body, isHtml = true)
  }

  def sendCheckOutConfirmation(visitorEmail: String, visitorName: String, checkOutTime: String): Future[Boolean] = {
    val subject = "Check-out Confirmation"
    val body = s"""
      |<html>
      |<body>
      |  <h2>Check-out Confirmation</h2>
      |  <p>Dear $visitorName,</p>
      |  <p>Thank you for visiting our office. Your check-out has been recorded at $checkOutTime.</p>
      |  <p>We hope to see you again soon!</p>
      |  <br/>
      |  <p>Best regards,<br/>Reception Team</p>
      |</body>
      |</html>
    """.stripMargin
    
    sendEmail(visitorEmail, subject, body, isHtml = true)
  }
}
