package com.example.mediscanauth.validation;

import com.example.mediscanauth.controller.auth.dto.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, RegisterRequest> {

    @Override
    public boolean isValid(RegisterRequest dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }
        String pwd = dto.getPassword();
        String confirm = dto.getConfirmPassword();
        if (pwd == null || confirm == null) {
            return true; // @NotBlank on fields will handle empties
        }
        boolean matches = pwd.equals(confirm);
        if (!matches) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("confirmPassword").addConstraintViolation();
        }
        return matches;
    }
}
