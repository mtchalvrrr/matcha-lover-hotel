package com.hotel.system.web;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class ConcurrencyExceptionAdvice {
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConcurrentUpdate(OptimisticLockingFailureException exception, Model model) {
        model.addAttribute("message",
                "Another request changed this room or reservation at the same time. Refresh the page and review the latest availability before trying again.");
        return "error/conflict";
    }
}
