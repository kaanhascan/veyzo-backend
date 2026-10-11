package com.veyzo.media_editing_system.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String username,
        @Email @NotBlank String email,
        @NotBlank(message = "Şifre boş bırakılamaz.")
        @Size(min = 8, message = "Şifre en az 8 karakter uzunluğunda olmalıdır.")
        @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).*$",
                message = "Şifre en az bir büyük harf, bir küçük harf ve bir rakam içermelidir.")
        String password
) {
}
