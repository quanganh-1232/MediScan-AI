package com.example.mediscanauth.controller.common.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProfileUpdateDTO {

    @NotBlank(message = "Họ và tên không được để trống.")
    @Size(min = 2, max = 50, message = "Họ và tên phải từ 2 đến 50 ký tự.")
    @Pattern(regexp = "^[a-zA-ZÀ-ỹ\\s]+$", message = "Họ và tên chỉ được chứa chữ cái và khoảng trắng.")
    private String fullName;

    @NotBlank(message = "Email không được để trống.")
    @Email(message = "Email không hợp lệ.")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự.")
    private String email;

    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải có đúng 10 chữ số và bắt đầu bằng số 0.")
    private String phone;

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
}
