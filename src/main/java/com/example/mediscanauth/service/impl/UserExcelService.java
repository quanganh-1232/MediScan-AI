package com.example.mediscanauth.service.impl;

import com.example.mediscanauth.model.Role;
import com.example.mediscanauth.model.User;
import com.example.mediscanauth.service.impl.UserAdminService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class UserExcelService {

    private static final String[] HEADERS = {"Full Name", "Email", "Phone", "Password", "Role", "Status"};
    private static final String[] ROLE_NAMES = {"ADMIN", "DOCTOR", "TECHNICIAN", "PATIENT", "RECEPTIONIST"};
    private static final String[] STATUS_VALUES = {"ACTIVE", "LOCKED"};

    private final UserAdminService userAdminService;

    public UserExcelService(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    // ---------- EXPORT ----------
    public ByteArrayOutputStream exportUsers(String keyword, String role, String status) throws Exception {
        List<User> users = userAdminService.filterUsers(keyword, role, status, 0, Integer.MAX_VALUE).getContent();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Users");
            CellStyle headerStyle = headerStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (User u : users) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(u.getFullName());
                row.createCell(1).setCellValue(u.getEmail());
                row.createCell(2).setCellValue(u.getPhone() == null ? "" : u.getPhone());
                row.createCell(3).setCellValue(""); // never export password hash
                row.createCell(4).setCellValue(u.getRole() != null ? u.getRole().getRoleName() : "");
                row.createCell(5).setCellValue(u.getStatus());
            }

            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out;
        }
    }

    // ---------- TEMPLATE (with Role/Status dropdown) ----------
    public ByteArrayOutputStream buildImportTemplate() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Users");
            CellStyle headerStyle = headerStyle(workbook);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // create some empty rows for the user to fill
            int templateRows = 50;

            addDropdownValidation(sheet, 4, templateRows, ROLE_NAMES);   // Role column (index 4)
            addDropdownValidation(sheet, 5, templateRows, STATUS_VALUES); // Status column (index 5)

            for (int i = 0; i < HEADERS.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out;
        }
    }

    private void addDropdownValidation(Sheet sheet, int columnIndex, int lastRow, String[] options) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
        CellRangeAddressList addressList = new CellRangeAddressList(1, lastRow, columnIndex, columnIndex);
        DataValidation validation = helper.createValidation(constraint, addressList);
        validation.setShowErrorBox(true);
        sheet.addValidationData(validation);
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    // ---------- IMPORT ----------
    public ImportResult importUsers(MultipartFile file) throws Exception {
        int success = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row, formatter)) continue;

                String fullName = formatter.formatCellValue(row.getCell(0)).trim();
                String email = formatter.formatCellValue(row.getCell(1)).trim();
                String phone = formatter.formatCellValue(row.getCell(2)).trim();
                String password = formatter.formatCellValue(row.getCell(3)).trim();
                String roleName = formatter.formatCellValue(row.getCell(4)).trim();
                String status = formatter.formatCellValue(row.getCell(5)).trim();

                try {
                    if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || roleName.isEmpty()) {
                        throw new IllegalArgumentException("Missing required field(s)");
                    }
                    userAdminService.createUserFromImport(fullName, email, phone, password,
                            roleName, status.isEmpty() ? "ACTIVE" : status);
                    success++;
                } catch (Exception rowEx) {
                    errors.add("Row " + (i + 1) + ": " + rowEx.getMessage());
                }
            }
        }

        return new ImportResult(success, errors.size(), errors);
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {
        for (int c = 0; c < HEADERS.length; c++) {
            if (!formatter.formatCellValue(row.getCell(c)).isBlank()) return false;
        }
        return true;
    }

    public record ImportResult(int successCount, int failCount, List<String> errors) {}
}