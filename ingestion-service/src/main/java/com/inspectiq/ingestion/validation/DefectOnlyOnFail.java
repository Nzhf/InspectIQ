package com.inspectiq.ingestion.validation;

import com.inspectiq.ingestion.dto.InspectionDtos.CreateInspectionRequest;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cross-field rule mirroring the DB CHECK constraint chk_defect_only_on_fail:
 * a PASS inspection must not carry a defect type. Validating here (instead of
 * relying on the DB) gives callers a clean 400 with a helpful message.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DefectOnlyOnFail.Validator.class)
public @interface DefectOnlyOnFail {

    String message() default "defectTypeCode is only allowed when result is FAIL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<DefectOnlyOnFail, CreateInspectionRequest> {

        @Override
        public boolean isValid(CreateInspectionRequest request, ConstraintValidatorContext context) {
            if (request == null || request.result() == null) {
                return true; // @NotNull on the field handles the null case
            }
            boolean pass = "PASS".equalsIgnoreCase(request.result());
            boolean hasDefect = request.defectTypeCode() != null && !request.defectTypeCode().isBlank();
            return !pass || !hasDefect;
        }
    }
}