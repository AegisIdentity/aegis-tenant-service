package io.aegis.tenant.web;

import io.aegis.tenant.service.CustomDomainService.DomainNotFoundException;
import io.aegis.tenant.service.CustomDomainService.DuplicateDomainException;
import io.aegis.tenant.service.CustomDomainService.InvalidDomainException;
import io.aegis.tenant.service.CustomDomainService.VerificationFailedException;
import io.aegis.tenant.service.TenantService.DuplicateTenantException;
import io.aegis.tenant.service.TenantService.TenantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TenantExceptionHandler {

    @ExceptionHandler(DuplicateTenantException.class)
    public ProblemDetail handleDuplicate(DuplicateTenantException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ProblemDetail handleNotFound(TenantNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateDomainException.class)
    public ProblemDetail handleDuplicateDomain(DuplicateDomainException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({InvalidDomainException.class, VerificationFailedException.class})
    public ProblemDetail handleBadDomainRequest(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(DomainNotFoundException.class)
    public ProblemDetail handleDomainNotFound(DomainNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
