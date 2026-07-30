package server.util;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class EmailUtil {
    // Thay thông tin Gmail của bạn vào đây
    private static final String SENDER_EMAIL = "hovietdanh53@gmail.com";
    private static final String SENDER_APP_PASSWORD = "nqywekoyuyszseyo";

    public static boolean sendOtpEmail(String recipientEmail, String otpCode) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_APP_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL, "FTP System"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipientEmail));
            message.setSubject("Mã xác thực OTP Đăng ký Tài khoản FTP");
            message.setText("Mã OTP của bạn là: " + otpCode + "\nMã này có hiệu lực trong vòng 5 phút.");

            Transport.send(message);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}