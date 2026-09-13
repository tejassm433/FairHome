package com.fairhome.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;

/** Keeps browser errors inside the dark themed layout rather than showing a bare Whitelabel page. */
@Controller
@ControllerAdvice(assignableTypes = {PublicController.class, AdminController.class})
public class WebErrorController {

    @ExceptionHandler(IllegalArgumentException.class)
    public String notFound(IllegalArgumentException e, Model model) {
        model.addAttribute("navPage", "error");
        model.addAttribute("heading", "We could not find that");
        model.addAttribute("detail", e.getMessage());
        return "error/message";
    }

    @ExceptionHandler(RuntimeException.class)
    public String unexpected(RuntimeException e, Model model) {
        model.addAttribute("navPage", "error");
        model.addAttribute("heading", "Something went wrong");
        model.addAttribute("detail", e.getMessage() == null
                ? e.getClass().getSimpleName() : e.getMessage());
        return "error/message";
    }
}
