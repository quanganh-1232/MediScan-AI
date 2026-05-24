package com.example.mediscanauth.controller.auth.dto;

import com.example.mediscanauth.validation.PasswordMatches;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordMatches
public class RegisterRequest {

    @NotBlank(message = "Họ và tên không được để trống.")
    @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự.")
    private String fullName;

    @NotBlank(message = "Email không được để trống.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự.")
    private String email;

    @NotBlank(message = "Số điện thoại không được để trống.")
    @Pattern(regexp = "^\\d{10}$", message = "Số điện thoại phải đủ 10 chữ số.")
    private String phone;

    @NotBlank(message = "Mật khẩu không được để trống.")
    @Size(min = 6, max = 72, message = "Mật khẩu phải từ 6 đến 72 ký tự.")
    private String password;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống.")
    @Size(min = 6, max = 72, message = "Xác nhận mật khẩu phải từ 6 đến 72 ký tự.")
    private String confirmPassword;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}