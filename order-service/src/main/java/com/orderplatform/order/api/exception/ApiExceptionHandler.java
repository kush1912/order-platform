package com.orderplatform.order.api.exception;

import com.orderplatform.order.domain.exception.IdempotencyConflictException;
import com.orderplatform.order.domain.exception.InvalidOrderStateException;
import com.orderplatform.order.domain.exception.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "Order not found",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail handleIdempotencyConflict(
            IdempotencyConflictException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Idempotency conflict",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    ProblemDetail handleInvalidOrderState(
            InvalidOrderStateException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "Invalid order state",
                exception.getMessage(),
                request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                "One or more request fields are invalid",
                request);
        List<Map<String, String>> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> Map.of(
                        "field",
                        error.getObjectName(),
                        "message",
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
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
