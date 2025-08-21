// File: com/syntex/islamicstudio/cli/CommandCategory.java
package com.syntex.islamicstudio.cli;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface CommandCategory {
    String value();
}