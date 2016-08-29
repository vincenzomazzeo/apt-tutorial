package it.ninjatech.apt.codegenerator;

import java.util.Date;

import javax.annotation.Generated;

import com.sun.codemodel.JAnnotationUse;
import com.sun.codemodel.JDefinedClass;

public final class Utils {

    private static final String ISO8601 = "%1$tFT%1$tT%1$tz";
    
    protected static void addGeneratedAnnotation(JDefinedClass definedClass) {
        JAnnotationUse generatedAnnotation = definedClass.annotate(Generated.class);
        generatedAnnotation.param("value", definedClass.fullName());
        generatedAnnotation.param("date", String.format(ISO8601, new Date()));
    }
    
    private Utils() {}
    
}
