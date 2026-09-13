package com.fairhome.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Keeps browser errors inside the themed layout rather than showing a bare Whitelabel page.
 */
@Controller
@ControllerAdvice(assignableTypes = {PublicController.class, AdminController.class})
public class WebErrorController implements ErrorController {

    @ExceptionHandler(IllegalArgumentException.class)
    public String notFound(IllegalArgumentException e, Model model) {
        return page(model, "We could not find that", e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public String unexpected(RuntimeException e, Model model) {
        return page(model, "Something went wrong", e.getMessage() == null
                ? e.getClass().getSimpleName() : e.getMessage());
    }

    @RequestMapping("/error")
    public String fallback(HttpServletRequest request, Model model) {
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        String detail = message != null && !message.toString().isBlank()
                ? message.toString()
                : exception != null && exception.getMessage() != null
                ? exception.getMessage()
                : "The page could not be shown.";
        return page(model, "Something went wrong", detail);
    }

    private static String page(Model model, String heading, String detail) {
        model.addAttribute("navPage", "error");
        model.addAttribute("heading", heading);
        model.addAttribute("detail", detail);
        return "error/message";
    }
}
