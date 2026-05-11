package example;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
class TenantCodeValidator implements ConstraintValidator<TenantCode, String> {

    @Override
    public boolean isValid(String value, AnnotationValue<TenantCode> annotationMetadata, ConstraintValidatorContext context) {
        return value != null && value.matches("[A-Z][A-Z0-9_-]{1,31}");
    }
}
