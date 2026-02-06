package com.borsibaar.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

        private ProblemDetail buildProblemDetail(HttpStatus status,
                        String title,
                        String detail,
                        String path) {
                ProblemDetail problemDetail = ProblemDetail.forStatus(status);
                problemDetail.setTitle(title);
                problemDetail.setDetail(detail);
                problemDetail.setProperty("timestamp", OffsetDateTime.now());
                problemDetail.setProperty("path", path);
                return problemDetail;
        }

        @ExceptionHandler(NotFoundException.class)
        public ProblemDetail handleNotFound(NotFoundException exception,
                        HttpServletRequest request) {
                return buildProblemDetail(
                                HttpStatus.NOT_FOUND,
                                "Not Found",
                                exception.getMessage(),
                                request.getRequestURI());
        }

        @ExceptionHandler(DuplicateResourceException.class)
        public ProblemDetail handleDuplicate(DuplicateResourceException exception,
                        HttpServletRequest request) {
                return buildProblemDetail(
                                HttpStatus.CONFLICT,
                                "Duplicate resource",
                                exception.getMessage(),
                                request.getRequestURI());
        }

        @ExceptionHandler(BadRequestException.class)
        public ProblemDetail handleBadRequest(BadRequestException exception,
                        HttpServletRequest request) {
                return buildProblemDetail(
                                HttpStatus.BAD_REQUEST,
                                "Bad request",
                                exception.getMessage(),
                                request.getRequestURI());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ProblemDetail handleValidation(MethodArgumentNotValidException exception,
                        HttpServletRequest request) {
                String detail = exception.getBindingResult().getFieldErrors().stream()
                                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                                .collect(Collectors.joining("; "));

                ProblemDetail problemDetail = buildProblemDetail(
                                HttpStatus.BAD_REQUEST,
                                "Validation failed",
                                detail,
                                request.getRequestURI());

                problemDetail.setProperty(
                                "errors",
                                exception.getBindingResult().getFieldErrors().stream()
                                                .collect(Collectors.toMap(
                                                                fieldError -> fieldError.getField(),
                                                                fieldError -> fieldError.getDefaultMessage(),
                                                                (first, second) -> first)));

                return problemDetail;
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ProblemDetail handleConstraintViolation(ConstraintViolationException exception,
                        HttpServletRequest request) {
                String detail = exception.getConstraintViolations().stream()
                                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                                .collect(Collectors.joining("; "));

                return buildProblemDetail(
                                HttpStatus.BAD_REQUEST,
                                "Constraint violation",
                                detail,
                                request.getRequestURI());
        }

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ProblemDetail handleDataIntegrity(DataIntegrityViolationException exception,
                        HttpServletRequest request) {
                return buildProblemDetail(
                                HttpStatus.CONFLICT,
                                "Data integrity violation",
                                "Resource already exists or constraint violated.",
                                request.getRequestURI());
        }

        @ExceptionHandler(ResponseStatusException.class)
        public ProblemDetail handleResponseStatus(ResponseStatusException exception,
                        HttpServletRequest request) {
                ProblemDetail problemDetail = ProblemDetail.forStatus(exception.getStatusCode());
                problemDetail.setTitle("Request failed");
                problemDetail.setDetail(exception.getReason());
                problemDetail.setProperty("timestamp", OffsetDateTime.now());
                problemDetail.setProperty("path", request.getRequestURI());
                return problemDetail;
        }

        @ExceptionHandler(ErrorResponseException.class)
        public ProblemDetail handleErrorResponse(ErrorResponseException exception,
                        HttpServletRequest request) {
                ProblemDetail problemDetail = exception.getBody();
                problemDetail.setProperty("timestamp", OffsetDateTime.now());
                problemDetail.setProperty("path", request.getRequestURI());
                return problemDetail;
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ProblemDetail handleNoResourceFound(NoResourceFoundException exception,
                        HttpServletRequest request) {
                return buildProblemDetail(
                                HttpStatus.NOT_FOUND,
                                "Not Found",
                                exception.getMessage(),
                                request.getRequestURI());
        }

        @ExceptionHandler(Exception.class)
        public ProblemDetail handleOther(Exception exception,
                        HttpServletRequest request) {
                log.error("Unhandled exception for {}", request.getRequestURI(), exception);
                ProblemDetail problemDetail = buildProblemDetail(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Server error",
                                "Unexpected error occurred.",
                                request.getRequestURI());

                problemDetail.setProperty("error", exception.getClass().getSimpleName());
                if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
                        problemDetail.setProperty("message", exception.getMessage());
                }
                return problemDetail;
        }
}
