package services

import com.typesafe.config.Config

import java.util.Properties
import javax.mail._
import javax.mail.internet.{InternetAddress, MimeMessage}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
 * SMTP email sender for booking confirmations, reminders, and auto-release notices.
 * Enforces safe configuration (Gmail App Password and matching from/user) at startup.
 */
class EmailService(config: Config)(implicit ec: ExecutionContext) {

  private val smtpHost: String = config.getString("email.smtp.host")
  private val smtpPort: Int = config.getInt("email.smtp.port")
  private val smtpUser: String = config.getString("email.smtp.user")
  private val smtpPassword: String = config.getString("email.smtp.password")
  private val fromEmail: String = config.getString("email.from")


  if (smtpUser.isEmpty || smtpPassword.isEmpty) {
    throw new IllegalStateException("EmailService configuration error: SMTP user/password must be configured and non-empty. Use a Gmail App Password, not your normal password.")
  }

  if (fromEmail.isEmpty) {
    throw new IllegalStateException("EmailService configuration error: email.from must be configured.")
  }

  if (!fromEmail.equalsIgnoreCase(smtpUser)) {
    throw new IllegalStateException(s"EmailService configuration error: email.from (=$fromEmail) must match email.smtp.user (=$smtpUser) and both must be the same Gmail account.")
  }


  private val props = new Properties()
  props.put("mail.smtp.auth", "true")
  props.put("mail.smtp.starttls.enable", "true")
  props.put("mail.smtp.starttls.required", "true")
  props.put("mail.smtp.host", smtpHost)
  props.put("mail.smtp.port", smtpPort)
  props.put("mail.smtp.ssl.trust", smtpHost)


  private val connectionTimeoutMs: String = config.hasPath("email.smtp.connectionTimeoutMs") match {
    case true => config.getInt("email.smtp.connectionTimeoutMs").toString
    case false => "10000"
  }
  private val readTimeoutMs: String = config.hasPath("email.smtp.readTimeoutMs") match {
    case true => config.getInt("email.smtp.readTimeoutMs").toString
    case false => "10000"
  }
  private val writeTimeoutMs: String = config.hasPath("email.smtp.writeTimeoutMs") match {
    case true => config.getInt("email.smtp.writeTimeoutMs").toString
    case false => "10000"
  }

  props.put("mail.smtp.connectiontimeout", connectionTimeoutMs)
  props.put("mail.smtp.timeout", readTimeoutMs)
  props.put("mail.smtp.writetimeout", writeTimeoutMs)


  println(s"EmailService SMTP config loaded: host=$smtpHost, port=$smtpPort, user=$smtpUser, from=$fromEmail, passwordSet=${smtpPassword.nonEmpty}, connectionTimeoutMs=$connectionTimeoutMs, readTimeoutMs=$readTimeoutMs, writeTimeoutMs=$writeTimeoutMs")

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

      println(s"[EmailService] Sending email - FROM: $fromEmail, TO: $to, Subject: $subject")
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


  def sendBookingConfirmation(employeeEmail: String, employeeName: String, roomName: String,
                             location: String, startTime: String, endTime: String, purpose: String): Future[Boolean] = {
    val subject = s"Meeting Room Reservation Confirmed: $roomName"
    val body = s"""
      |<html>
      |<body>
      |  <h2>Meeting Room Reservation Confirmed</h2>
      |  <p>Dear $employeeName,</p>
      |  <p>Your meeting room reservation has been confirmed:</p>
      |  <ul>
      |    <li><strong>Room:</strong> $roomName</li>
      |    <li><strong>Location:</strong> $location</li>
      |    <li><strong>Date & Time:</strong> $startTime to $endTime</li>
      |    <li><strong>Purpose:</strong> $purpose</li>
      |  </ul>
      |  <p>Please arrive on time. The room will be prepared for your meeting.</p>
      |  <p><strong>Note:</strong> You will receive a reminder 15 minutes before your reservation time.</p>
      |  <br/>
      |  <p>Best regards,<br/>Office Management System</p>
      |</body>
      |</html>
    """.stripMargin

    sendEmail(employeeEmail, subject, body, isHtml = true)
  }

  def sendReservationReminder(employeeEmail: String, employeeName: String, roomName: String,
                             location: String, startTime: String): Future[Boolean] = {
    val subject = s"Reminder: Meeting Room Reservation in 15 minutes - $roomName"
    val body = s"""
      |<html>
      |<body>
      |  <h2>Meeting Room Reservation Reminder</h2>
      |  <p>Dear $employeeName,</p>
      |  <p>This is a reminder that your meeting room reservation starts in 15 minutes:</p>
      |  <ul>
      |    <li><strong>Room:</strong> $roomName</li>
      |    <li><strong>Location:</strong> $location</li>
      |    <li><strong>Start Time:</strong> $startTime</li>
      |  </ul>
      |  <p>Please proceed to the room at the scheduled time.</p>
      |  <br/>
      |  <p>Best regards,<br/>Office Management System</p>
      |</body>
      |</html>
    """.stripMargin

    sendEmail(employeeEmail, subject, body, isHtml = true)
  }

  def sendAutoReleaseNotification(employeeEmail: String, employeeName: String, roomName: String,
                                 startTime: String): Future[Boolean] = {
    val subject = s"Meeting Room Reservation Auto-Released: $roomName"
    val body = s"""
      |<html>
      |<body>
      |  <h2>Meeting Room Reservation Auto-Released</h2>
      |  <p>Dear $employeeName,</p>
      |  <p>Your meeting room reservation has been automatically released because the room was not used within 15 minutes of the scheduled start time.</p>
      |  <ul>
      |    <li><strong>Room:</strong> $roomName</li>
      |    <li><strong>Scheduled Start Time:</strong> $startTime</li>
      |  </ul>
      |  <p>If you still need the room, please make a new reservation.</p>
      |  <br/>
      |  <p>Best regards,<br/>Office Management System</p>
      |</body>
      |</html>
    """.stripMargin

    sendEmail(employeeEmail, subject, body, isHtml = true)
  }
}
