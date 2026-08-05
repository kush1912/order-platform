package com.orderplatform.notification.api.dto.request;

import com.orderplatform.notification.domain.entity.NotificationChannel;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record NotificationDispatchRequest(
        @NotBlank String clientReference,
        @NotEmpty Set<NotificationChannel> channels,
        @Email String email,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phoneNumber,
        String subject,
        @NotBlank String message,
        String whatsappTemplateName) {

    @AssertTrue(message = "email and subject are required for EMAIL notifications")
    public boolean isEmailConfigurationValid() {
        return channels == null
                || !channels.contains(NotificationChannel.EMAIL)
                || (hasText(email) && hasText(subject));
    }

    @AssertTrue(message = "phoneNumber is required for SMS and WHATSAPP notifications")
    public boolean isPhoneConfigurationValid() {
        return channels == null
                || (!channels.contains(NotificationChannel.SMS)
                && !channels.contains(NotificationChannel.WHATSAPP))
                || hasText(phoneNumber);
    }

    @AssertTrue(message = "whatsappTemplateName is required for WHATSAPP notifications")
    public boolean isWhatsappConfigurationValid() {
        return channels == null
                || !channels.contains(NotificationChannel.WHATSAPP)
                || hasText(whatsappTemplateName);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
