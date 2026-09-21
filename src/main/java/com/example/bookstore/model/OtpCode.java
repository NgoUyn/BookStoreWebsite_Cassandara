package com.example.bookstore.model;

import com.example.bookstore.model.document.SequencedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * Collection {@code otp_codes} - ma OTP gui email.
 *
 * <p>Truoc day OTP chi luu trong RAM (HashMap cua AuthOtpService) nen mat khi
 * restart va khong chay duoc nhieu instance. Nay luu MongoDB voi TTL index
 * tren {@code expiresAt} => het han tu dong, khong can job.</p>
 */
@Document(collection = "otp_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode implements SequencedDocument {

    @Id
    private Long id;

    private String email;

    private String code;

    /** REGISTER | RESET_PASSWORD | ... */
    private String purpose;

    private Integer attempts;

    private Boolean verified;

    @Field("createdAt")
    private LocalDateTime createdAt;

    /** TTL index (expireAfterSeconds = 0) - MongoDB tu xoa khi het han. */
    private LocalDateTime expiresAt;
}
