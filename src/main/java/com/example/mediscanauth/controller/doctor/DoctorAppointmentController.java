package com.example.mediscanauth.controller.doctor;

import com.example.mediscanauth.model.Appointment;
import com.example.mediscanauth.repository.AppointmentRepository;
import com.example.mediscanauth.service.ImagingRecordService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/doctor")
public class DoctorAppointmentController {

    private final AppointmentRepository appointmentRepository;
    private final ImagingRecordService imagingRecordService; // Dùng để lấy doctorId từ email đăng nhập

    public DoctorAppointmentController(AppointmentRepository appointmentRepository, ImagingRecordService imagingRecordService) {
        this.appointmentRepository = appointmentRepository;
        this.imagingRecordService = imagingRecordService;
    }

    @GetMapping("/appointments")
    public String listDoctorAppointments(@RequestParam(required = false) String status, 
                                         Authentication authentication, 
                                         Model model) {
        // 1. Lấy email tài khoản đang đăng nhập hiện tại
        String email = authentication.getName();
        
        // 2. Tận dụng hàm có sẵn trong ImagingRecordServiceImpl để lấy đúng ID của bác sĩ
        Long doctorId = imagingRecordService.getDoctorIdByEmail(email);
        
        List<Appointment> appointments;
        // 3. Tiến hành lọc dữ liệu theo Trạng thái (nếu có chọn)
        if (status != null && !status.isEmpty()) {
            appointments = appointmentRepository.findByDoctorUserIdAndStatusOrderByScheduledTimeDesc(doctorId, status);
        } else {
            appointments = appointmentRepository.findByDoctorUserIdOrderByScheduledTimeDesc(doctorId);
        }

        // 4. Gửi dữ liệu sang giao diện Thymeleaf
        model.addAttribute("appointments", appointments);
        model.addAttribute("selectedStatus", status != null ? status : "");
        
        return "doctor/appointments"; // Đường dẫn file HTML: src/main/resources/templates/doctor/appointments.html
    }
}