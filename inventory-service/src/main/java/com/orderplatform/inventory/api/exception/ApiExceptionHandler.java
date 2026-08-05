package com.orderplatform.inventory.api.exception;

import com.orderplatform.inventory.domain.exception.InsufficientOnHandQuantityException;
import com.orderplatform.inventory.domain.exception.InventoryNotFoundException;
import com.orderplatform.inventory.domain.exception.InventoryPreconditionException;
import com.orderplatform.inventory.domain.exception.InventoryVersionConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InventoryNotFoundException.class)
    ProblemDetail handleNotFound(InventoryNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Inventory not found", exception.getMessage(), request);
    }

    @ExceptionHandler(InventoryVersionConflictException.class)
    ProblemDetail handleVersionConflict(
            InventoryVersionConflictException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.PRECONDITION_FAILED,
                "Inventory version conflict",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(InventoryPreconditionException.class)
    ProblemDetail handlePrecondition(
            InventoryPreconditionException exception,
            HttpServletRequest request) {
        HttpStatus status = exception.missing()
                ? HttpStatus.PRECONDITION_REQUIRED
                : HttpStatus.BAD_REQUEST;
        return problem(status, "Invalid inventory precondition", exception.getMessage(), request);
    }

    @ExceptionHandler(InsufficientOnHandQuantityException.class)
    ProblemDetail handleQuantity(
            InsufficientOnHandQuantityException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Inventory constraint violated",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request fields are invalid",
                request);
    }

    private ProblemDetail problem(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://orderplatform.local/problems/"
                + title.toLowerCase().replace(' ', '-')));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
