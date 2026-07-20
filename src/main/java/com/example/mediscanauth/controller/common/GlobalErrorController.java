package com.example.mediscanauth.controller.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Global error handler - redirects all errors to /home with a toast
 * instead of showing the Spring Boot white-label error page.
 */
@Controller
public class GlobalErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        Object statusCode = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String msg = "Co loi xay ra. Vui long thu lai.";
        if (statusCode != null) {
            int code = Integer.parseInt(statusCode.toString());
            if (code == 404) msg = "Trang nay khong ton tai.";
            else if (code == 403) msg = "Ban khong co quyen truy cap trang nay.";
            else if (code == 500) msg = "Loi may chu. Vui long thu lai sau.";
        }
        redirectAttributes.addFlashAttribute("toastMsg", msg);
        redirectAttributes.addFlashAttribute("toastType", "error");
        return "redirect:/home";
    }
}

