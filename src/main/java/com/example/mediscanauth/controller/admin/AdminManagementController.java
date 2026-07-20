package com.example.mediscanauth.controller.admin;

import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.impl.AdminManagementService;
import com.example.mediscanauth.service.impl.UserAdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class AdminManagementController {

    private final UserAdminService userAdminService;
    private final AdminManagementService adminManagementService;

    public AdminManagementController(UserAdminService userAdminService,
                                     AdminManagementService adminManagementService) {
        this.userAdminService = userAdminService;
        this.adminManagementService = adminManagementService;
    }

    @GetMapping("/admin/doctors")
    public String doctors(@RequestParam(required = false) String keyword, Model model) {
        List<User> doctors = userAdminService.findStaffByRole("DOCTOR", keyword);
        model.addAttribute("doctors", doctors);
        model.addAttribute("keyword", text(keyword));
        model.addAttribute("activeCount", doctors.stream().filter(user -> "ACTIVE".equals(user.getStatus())).count());
        return "admin/doctors";
    }

    @PostMapping("/admin/doctors")
    public String createDoctor(@RequestParam String fullName, @RequestParam String email,
                               @RequestParam(required = false) String phone, @RequestParam String password,
                               RedirectAttributes redirect) {
        return run("/admin/doctors", redirect, "Đã thêm bác sĩ mới.",
                () -> userAdminService.createStaff(fullName, email, phone, password, "DOCTOR"));
    }

    @PostMapping("/admin/doctors/{id}/update")
    public String updateDoctor(@PathVariable Long id, @RequestParam String fullName,
                               @RequestParam String email, @RequestParam(required = false) String phone,
                               RedirectAttributes redirect) {
        return run("/admin/doctors", redirect, "Đã cập nhật thông tin bác sĩ.",
                () -> userAdminService.updateStaff(id, fullName, email, phone, "DOCTOR"));
    }

    @PostMapping("/admin/doctors/{id}/status")
    public String updateDoctorStatus(@PathVariable Long id, @RequestParam String status,
                                     RedirectAttributes redirect) {
        return run("/admin/doctors", redirect, "Đã cập nhật trạng thái bác sĩ.",
                () -> userAdminService.updateStaffStatus(id, status, "DOCTOR"));
    }

    @GetMapping("/admin/technicians")
    public String technicians(@RequestParam(required = false) String keyword, Model model) {
        List<User> technicians = userAdminService.findStaffByRole("TECHNICIAN", keyword);
        Map<Long, Long> completedCases = new LinkedHashMap<>();
        technicians.forEach(item -> completedCases.put(item.getUserId(),
                adminManagementService.completedCases(item.getUserId())));
        model.addAttribute("technicians", technicians);
        model.addAttribute("completedCases", completedCases);
        model.addAttribute("keyword", text(keyword));
        model.addAttribute("appointments", adminManagementService.findAppointments(null, null).stream()
                .filter(item -> !List.of("COMPLETED", "CANCELLED").contains(item.getStatus()))
                .toList());
        return "admin/technicians";
    }

    @PostMapping("/admin/technicians")
    public String createTechnician(@RequestParam String fullName, @RequestParam String email,
                                   @RequestParam(required = false) String phone, @RequestParam String password,
                                   RedirectAttributes redirect) {
        return run("/admin/technicians", redirect, "Đã thêm kỹ thuật viên mới.",
                () -> userAdminService.createStaff(fullName, email, phone, password, "TECHNICIAN"));
    }

    @PostMapping("/admin/technicians/{id}/update")
    public String updateTechnician(@PathVariable Long id, @RequestParam String fullName,
                                   @RequestParam String email, @RequestParam(required = false) String phone,
                                   RedirectAttributes redirect) {
        return run("/admin/technicians", redirect, "Đã cập nhật kỹ thuật viên.",
                () -> userAdminService.updateStaff(id, fullName, email, phone, "TECHNICIAN"));
    }

    @PostMapping("/admin/technicians/{id}/status")
    public String updateTechnicianStatus(@PathVariable Long id, @RequestParam String status,
                                         RedirectAttributes redirect) {
        return run("/admin/technicians", redirect, "Đã cập nhật trạng thái kỹ thuật viên.",
                () -> userAdminService.updateStaffStatus(id, status, "TECHNICIAN"));
    }

    @PostMapping("/admin/technicians/{id}/delete")
    public String deleteTechnician(@PathVariable Long id, RedirectAttributes redirect) {
        return run("/admin/technicians", redirect, "Đã xóa kỹ thuật viên.",
                () -> adminManagementService.deleteTechnician(id));
    }

    @PostMapping("/admin/appointments/{id}/assign-technician")
    public String assignTechnician(@PathVariable Long id, @RequestParam(required = false) Long technicianId,
                                   @RequestParam(defaultValue = "/admin/technicians") String returnTo,
                                   RedirectAttributes redirect) {
        String safeReturnTo = returnTo.startsWith("/admin/") ? returnTo : "/admin/technicians";
        return run(safeReturnTo, redirect, "Đã phân công kỹ thuật viên.",
                () -> adminManagementService.assignTechnician(id, technicianId));
    }

    @GetMapping("/admin/patients")
    public String patients(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("patients", adminManagementService.findPatients(keyword));
        model.addAttribute("keyword", text(keyword));
        return "admin/patients";
    }

    @PostMapping("/admin/patients/{id}/update")
    public String updatePatient(@PathVariable Long id, @RequestParam String fullName,
                                @RequestParam(required = false) String phone, @RequestParam String gender,
                                @RequestParam(required = false) String address,
                                @RequestParam(required = false) String medicalHistory,
                                RedirectAttributes redirect) {
        return run("/admin/patients", redirect, "Đã cập nhật hồ sơ bệnh nhân.",
                () -> adminManagementService.updatePatient(id, fullName, phone, gender, address, medicalHistory));
    }

    @PostMapping("/admin/patients/{id}/lock")
    public String lockPatient(@PathVariable Long id, @RequestParam boolean locked, RedirectAttributes redirect) {
        return run("/admin/patients", redirect, locked ? "Đã khóa hồ sơ bệnh nhân." : "Đã mở khóa hồ sơ bệnh nhân.",
                () -> adminManagementService.setPatientLocked(id, locked));
    }

    @GetMapping("/admin/appointments")
    public String appointments(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String status, Model model) {
        model.addAttribute("appointments", adminManagementService.findAppointments(keyword, status));
        model.addAttribute("technicians", userAdminService.findStaffByRole("TECHNICIAN", null));
        model.addAttribute("doctors", userAdminService.findStaffByRole("DOCTOR", null));
        model.addAttribute("statuses", adminManagementService.appointmentStatuses());
        model.addAttribute("keyword", text(keyword));
        model.addAttribute("selectedStatus", text(status));
        return "admin/appointments";
    }

    @PostMapping("/admin/appointments/{id}/update")
    public String updateAppointment(@PathVariable Long id,
                                    @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") LocalDateTime scheduledTime,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) String note,
                                    @RequestParam String status,
                                    @RequestParam(required = false) Long technicianId,
                                    @RequestParam(required = false) Long doctorId,
                                    RedirectAttributes redirect) {
        return run("/admin/appointments", redirect, "Đã cập nhật lịch hẹn.",
                () -> adminManagementService.updateAppointment(id, scheduledTime, location, note, status,
                        technicianId, doctorId));
    }

    @PostMapping("/admin/appointments/{id}/cancel")
    public String cancelAppointment(@PathVariable Long id, RedirectAttributes redirect) {
        return run("/admin/appointments", redirect, "Đã hủy lịch hẹn.",
                () -> adminManagementService.cancelAppointment(id));
    }

    private String run(String path, RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("success", success);
        } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + path;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
