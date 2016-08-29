package it.ninjatech.apt.codegenerator;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public final class ModelGenerator {

    private static final String KEY_QUALIFIED_NAME_METHOD_NAME = "keyQualifiedName";
    private static final String NOTIFY_UPDATE_METHOD_NAME = "notifyUpdate";
    private static final String GET_METHOD_NAME_PATTERN = "get%s";
    private static final String SET_METHOD_NAME_PATTERN = "set%s";

    private final JCodeModel codeModel;

    protected ModelGenerator(JCodeModel codeModel) {
        this.codeModel = codeModel;
    }

    protected void generate(String ancestorQualifiedName, String modelQualifiedName, String keyQualifiedName,
                            List<FieldInfo> fieldInfos) throws JClassAlreadyExistsException, ClassNotFoundException {
        JClass ancestorType = this.codeModel.ref(ancestorQualifiedName);

        JDefinedClass modelClass = this.codeModel._class(modelQualifiedName, ClassType.CLASS);
        modelClass._extends(ancestorType);

         Utils.addGeneratedAnnotation(modelClass);

        JMethod keyQualifiedNameMethod = modelClass.method(JMod.PUBLIC, String.class, KEY_QUALIFIED_NAME_METHOD_NAME);
        keyQualifiedNameMethod.annotate(Override.class);
        keyQualifiedNameMethod.body()._return(JExpr.lit(keyQualifiedName));

        for (FieldInfo fieldInfo : fieldInfos) {
            JType type = this.codeModel.parseType(fieldInfo.getType());

            // Get
            JMethod getMethod = modelClass.method(JMod.PUBLIC, type, String.format(GET_METHOD_NAME_PATTERN, StringUtils.capitalize(fieldInfo.getName())));
            getMethod.body()._return(JExpr._this().ref(fieldInfo.getName()));

            // Set
            JMethod setMethod = modelClass.method(JMod.PUBLIC, this.codeModel.VOID,
                                                  String.format(SET_METHOD_NAME_PATTERN, StringUtils.capitalize(fieldInfo.getName())));
            JVar setParam = setMethod.param(type, fieldInfo.getName());
            setMethod.body()
                     .assign(JExpr._this().ref(fieldInfo.getName()), setParam)
                     .invoke(NOTIFY_UPDATE_METHOD_NAME);
        }

    }

}
