package it.ninjatech.apt.codegenerator;

import java.io.IOException;
import java.io.OutputStream;

import javax.annotation.processing.Filer;

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JPackage;

public class FilerCodeWriter extends CodeWriter {

    private static String stripJavaSuffix(String fileName) {
        int pos = fileName.lastIndexOf(".java");
        return pos > -1 ? fileName.substring(0, pos) : fileName;
    }
    
    private final Filer filer;
    private OutputStream outputStream;

    public FilerCodeWriter(Filer filer) {
        this.filer = filer;
    }

    @Override
    public OutputStream openBinary(JPackage pkg, String fileName) throws IOException {
        String qualifiedName = String.format("%s.%s", pkg.name(), stripJavaSuffix(fileName));
        this.outputStream = this.filer.createSourceFile(qualifiedName).openOutputStream();
        
        return this.outputStream;
    }

    @Override
    public void close() throws IOException {
        if (this.outputStream != null) {
            this.outputStream.close();
        }
    }
    
}