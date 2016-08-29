package it.ninjatech.apt.codegenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;

import it.ninjatech.apt.codegenerator.annotation.Model;
import it.ninjatech.apt.codegenerator.annotation.ModelId;

@SupportedAnnotationTypes({
                            "it.ninjatech.apt.codegenerator.annotation.Model"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ModelProcessor extends AbstractProcessor {

    private static final String MODEL_QUALIFIED_NAME_PATTERN = "%s%s";
    private static final String KEY_QUALIFIED_NAME_PATTERN = "%skey.%sKey";

    private Filer filer;
    private Messager messager;
    private Elements elementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        this.filer = processingEnvironment.getFiler();
        this.messager = processingEnvironment.getMessager();
        this.elementUtils = processingEnvironment.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        try {
            Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(Model.class);
            if (!elements.isEmpty()) {
                JCodeModel codeModel = new JCodeModel();
                ModelGenerator modelGenerator = new ModelGenerator(codeModel);
                ModelKeyGenerator modelKeyGenerator = new ModelKeyGenerator(codeModel);

                for (Element element : elements) {
                    if (element.getKind() == ElementKind.CLASS) {
                        TypeElement modelType = (TypeElement) element;
                        generate(modelType, modelGenerator, modelKeyGenerator);
                        this.messager.printMessage(Kind.NOTE, String.format("[Model] Generated class %s", modelType.getQualifiedName().toString()));
                    }
                }

                codeModel.build(new FilerCodeWriter(this.filer));
            }
        }
        catch (Exception e) {
            this.messager.printMessage(Kind.ERROR, e.getMessage());
        }

        return true;
    }

    private void generate(TypeElement modelType, ModelGenerator modelGenerator, ModelKeyGenerator modelKeyGenerator) throws IOException,
                                                                                                                     ClassNotFoundException,
                                                                                                                     JClassAlreadyExistsException {
        PackageElement pkgElement = this.elementUtils.getPackageOf(modelType);
        String pkg = pkgElement.isUnnamed() ? "" : String.format("%s.", pkgElement.getQualifiedName().toString());
        String modelName = modelType.getSimpleName().toString().substring(Modifier.ABSTRACT.toString().length());
        String ancestorQualifiedName = modelType.getQualifiedName().toString();
        String modelQualifiedName = String.format(MODEL_QUALIFIED_NAME_PATTERN, pkg, modelName);
        String keyQualifiedName = String.format(KEY_QUALIFIED_NAME_PATTERN, pkg, modelName);

        List<FieldInfo> fieldInfos = new ArrayList<>();
        List<FieldInfo> idFieldInfos = new ArrayList<>();
        List<? extends Element> enclosedElements = modelType.getEnclosedElements();
        for (Element enclosedElement : enclosedElements) {
            if (enclosedElement.getKind() == ElementKind.FIELD) {
                VariableElement fieldElement = (VariableElement) enclosedElement;
                if (!fieldElement.getModifiers().contains(Modifier.STATIC)) {
                    FieldInfo fieldInfo = new FieldInfo(fieldElement.asType().toString(), fieldElement.getSimpleName().toString());
                    fieldInfos.add(fieldInfo);
                    List<? extends AnnotationMirror> annotationMirrors = this.elementUtils.getAllAnnotationMirrors(fieldElement);
                    for (AnnotationMirror annotationMirror : annotationMirrors) {
                        if (annotationMirror.getAnnotationType().toString().equals(ModelId.class.getName())) {
                            idFieldInfos.add(fieldInfo);
                        }
                    }
                }
            }
        }
        modelGenerator.generate(ancestorQualifiedName, modelQualifiedName, keyQualifiedName, fieldInfos);
        modelKeyGenerator.generateKey(modelQualifiedName, keyQualifiedName, idFieldInfos);
    }

}
