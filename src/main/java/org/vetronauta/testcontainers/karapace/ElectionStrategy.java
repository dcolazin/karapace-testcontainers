package org.vetronauta.testcontainers.karapace;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ElectionStrategy {

    HIGHEST("highest"), LOWEST("lowest");

    private final String propertyValue;

}
