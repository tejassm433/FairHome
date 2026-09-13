package com.fairhome.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(WebErrorController.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public String notFound(IllegalArgumentException e, Model model) {
        log.debug("FairHome : WebErrorController : in method notFound : START");
        log.warn("FairHome : WebErrorController : in method notFound : not found : {}", e.getMessage());
        String view = page(model, "We could not find that", e.getMessage());
        log.debug("FairHome : WebErrorController : in method notFound : END");
        return view;
    }

    @ExceptionHandler(RuntimeException.class)
    public String unexpected(RuntimeException e, Model model) {
        log.debug("FairHome : WebErrorController : in method unexpected : START");
        log.error("FairHome : WebErrorController : in method unexpected : error : {}", e.getMessage(), e);
        String view = page(model, "Something went wrong", e.getMessage() == null
                ? e.getClass().getSimpleName() : e.getMessage());
        log.debug("FairHome : WebErrorController : in method unexpected : END");
        return view;
    }

    @RequestMapping("/error")
    public String fallback(HttpServletRequest request, Model model) {
        log.debug("FairHome : WebErrorController : in method fallback : START");
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Throwable exception = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        String detail = message != null && !message.toString().isBlank()
                ? message.toString()
                : exception != null && exception.getMessage() != null
                ? exception.getMessage()
                : "The page could not be shown.";
        if (exception != null) {
            log.error("FairHome : WebErrorController : in method fallback : error : {}", detail, exception);
        } else {
            log.warn("FairHome : WebErrorController : in method fallback : error page : {}", detail);
        }
        String view = page(model, "Something went wrong", detail);
        log.debug("FairHome : WebErrorController : in method fallback : END");
        return view;
    }

    private static String page(Model model, String heading, String detail) {
        log.debug("FairHome : WebErrorController : in method page : START");
        model.addAttribute("navPage", "error");
        model.addAttribute("heading", heading);
        model.addAttribute("detail", detail);
        log.debug("FairHome : WebErrorController : in method page : END");
        return "error/message";
    }
}
