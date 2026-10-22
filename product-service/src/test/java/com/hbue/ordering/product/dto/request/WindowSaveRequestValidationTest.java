package com.hbue.ordering.product.dto.request;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowSaveRequestValidationTest {

    @Test
    void shouldValidateDescriptionLengthAndNonnegativeSortWithoutRequiringNewFields() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            WindowSaveRequest request = new WindowSaveRequest();
            assertTrue(validator.validateProperty(request, "description").isEmpty());
            assertTrue(validator.validateProperty(request, "sortOrder").isEmpty());

            request.setDescription("字".repeat(500));
            request.setSortOrder(0);
            assertTrue(validator.validateProperty(request, "description").isEmpty());
            assertTrue(validator.validateProperty(request, "sortOrder").isEmpty());

            request.setDescription("字".repeat(501));
            request.setSortOrder(-1);
            assertFalse(validator.validateProperty(request, "description").isEmpty());
            assertFalse(validator.validateProperty(request, "sortOrder").isEmpty());
        }
    }
}
