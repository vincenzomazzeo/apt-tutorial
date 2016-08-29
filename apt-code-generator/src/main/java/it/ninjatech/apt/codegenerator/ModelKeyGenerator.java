package it.ninjatech.apt.codegenerator;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.sun.codemodel.ClassType;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JVar;

public final class ModelKeyGenerator {

    private static final String MODEL_PARAM_NAME = "model";
    private static final String OBJECT_PARAM_NAME = "obj";
    private static final String OTHER_PARAM_NAME = "other";
    private static final String PRIME_VAR_NAME = "prime";
    private static final String RESULT_VAR_NAME = "result";
    private static final String HASH_CODE_METHOD_NAME = "hashCode";
    private static final String EQUALS_METHOD_NAME = "equals";
    private static final String GET_CLASS_METHOD_NAME = "getClass";
    private static final String GET_METHOD_NAME_PATTERN = "get%s";
    
    private static final String ABSTRACT_MODEL_KEY_QUALIFIED_NAME = "it.ninjatech.apt.example.AbstractModelKey";
    
    private final JCodeModel codeModel;
    
    protected ModelKeyGenerator(JCodeModel codeModel) {
        this.codeModel = codeModel;
    }
    
    protected void generateKey(String modelQualifiedName, String keyQualifiedName, 
                             List<FieldInfo> idFieldInfos) throws JClassAlreadyExistsException {
        JClass modelType = this.codeModel.ref(modelQualifiedName);

        JDefinedClass keyClass = this.codeModel._class(keyQualifiedName, ClassType.CLASS);
        keyClass._extends(this.codeModel.ref(ABSTRACT_MODEL_KEY_QUALIFIED_NAME).narrow(modelType));

        Utils.addGeneratedAnnotation(keyClass);

        // Constructor Model
        JMethod constructorModel = keyClass.constructor(JMod.PUBLIC);
        JVar modelVar = constructorModel.param(modelType, MODEL_PARAM_NAME);
        JBlock constructorModelBody = constructorModel.body();

        // Constructor Fields
        JMethod constructorFields = keyClass.constructor(JMod.PUBLIC);
        JBlock constructorFieldsBody = constructorFields.body();

        // HashCode
        JMethod hashCodeMethod = keyClass.method(JMod.PUBLIC, this.codeModel.INT, HASH_CODE_METHOD_NAME);
        hashCodeMethod.annotate(Override.class);
        JBlock hashCodeBody = hashCodeMethod.body();
        JVar hashCodePrimeVar = hashCodeBody.decl(JMod.FINAL, this.codeModel.INT, PRIME_VAR_NAME, JExpr.lit(31));
        JVar hashCodeResultVar = hashCodeBody.decl(this.codeModel.INT, RESULT_VAR_NAME, JExpr.lit(1));

        // Equals
        JMethod equalsMethod = keyClass.method(JMod.PUBLIC, this.codeModel.BOOLEAN, EQUALS_METHOD_NAME);
        JVar equalsObjVar = equalsMethod.param(Object.class, OBJECT_PARAM_NAME);
        equalsMethod.annotate(Override.class);
        JBlock equalsBody = equalsMethod.body();
        equalsBody._if(JExpr._this().eq(equalsObjVar))._then()._return(JExpr.TRUE);
        equalsBody._if(equalsObjVar.eq(JExpr._null()))._then()._return(JExpr.FALSE);
        equalsBody._if(JExpr.invoke(GET_CLASS_METHOD_NAME).ne(equalsObjVar.invoke(GET_CLASS_METHOD_NAME)))._then()._return(JExpr.FALSE);
        JVar equalsOtherVar = equalsBody.decl(keyClass, OTHER_PARAM_NAME, JExpr.cast(keyClass, equalsObjVar));

        for (FieldInfo fieldInfo : idFieldInfos) {
            JClass fieldClass = this.codeModel.ref(fieldInfo.getType());
            JFieldVar fieldVar = keyClass.field(JMod.PRIVATE | JMod.FINAL, fieldClass, fieldInfo.getName());

            String capitalizedField = StringUtils.capitalize(fieldInfo.getName());

            // Constructor Model
            constructorModelBody.assign(JExpr._this().ref(fieldVar), modelVar.invoke(String.format(GET_METHOD_NAME_PATTERN, capitalizedField)));

            // Constructor Fields
            JVar constructorFieldVar = constructorFields.param(fieldClass, fieldInfo.getName());
            constructorFieldsBody.assign(JExpr._this().ref(fieldVar), constructorFieldVar);

            // Fill HashCode
            JExpression fieldNullTernary = JOp.cond(fieldVar.eq(JExpr._null()), JExpr.lit(0), fieldVar.invoke(HASH_CODE_METHOD_NAME));
            hashCodeBody.assign(hashCodeResultVar, hashCodePrimeVar.mul(hashCodeResultVar).plus(fieldNullTernary));

            // Fill Equals
            JConditional ifBlock = equalsBody._if(JExpr._this().ref(fieldVar).eq(JExpr._null()));
            ifBlock._then()._if(equalsOtherVar.ref(fieldInfo.getName()).ne(JExpr._null()))._then()._return(JExpr.FALSE);
            ifBlock._elseif(JExpr._this().ref(fieldVar).invoke(EQUALS_METHOD_NAME).arg(equalsOtherVar.ref(fieldInfo.getName())).not())._then()._return(JExpr.FALSE);

            // Get
            JMethod getMethod = keyClass.method(JMod.PUBLIC, fieldClass, String.format(GET_METHOD_NAME_PATTERN, capitalizedField));
            getMethod.body()._return(JExpr._this().ref(fieldVar));
        }

        hashCodeBody._return(hashCodeResultVar);
        equalsBody._return(JExpr.TRUE);
    }
    
}
