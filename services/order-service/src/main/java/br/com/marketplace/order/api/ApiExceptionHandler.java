package br.com.marketplace.order.api;

import br.com.marketplace.order.application.OrderExceptions.IdempotencyConflictException;
import br.com.marketplace.order.application.OrderExceptions.InvalidOrderException;
import br.com.marketplace.order.application.OrderExceptions.TooManyRequestsException;
import br.com.marketplace.order.application.OrderExceptions.UnknownProductException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros como RFC 7807 (application/problem+json). */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler({InvalidOrderException.class, MissingRequestHeaderException.class})
    ProblemDetail invalid(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownProductException.class)
    ProblemDetail unknownProduct(UnknownProductException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ProblemDetail tooMany(TooManyRequestsException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }
}
