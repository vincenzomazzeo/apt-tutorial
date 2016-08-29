Using Annotation Processing Tool (APT) for Java code generation
===========================================

A tutorial about Java Annotation Processing Tool.

## Table of Contents
* [Introduction]
* [Overview]
* [APT Code Generator Project]
	* [Annotations]
		* [Model]
		* [ModelId]
	* [ModelProcessor]
	* [SPI]
	* [POM]
* [APT Example Project]
	* [ModelJournalMonitor]
	* [AbstractModel]
	* [AbstractModelKey]
	* [AbstractProduct]
		* [Product]
		* [ProductKey]
* [Integration with Eclipse]
* [Conclusion]
* [License]
* [References]

## Introduction
“The `apt` tool is a command-line utility for annotation processing. It includes a set of reflective APIs and supporting infrastructure to process program annotations (JSR 175). These reflective APIs provide a build-time, source-based, read-only view of program structure. They are designed to cleanly model the Java programming language's type system after the addition of generics (JSR 14).

The `apt` tool first runs annotation processors that can produce new source code and other files. Next, `apt` can cause compilation of both original and generated source files, thus easing the development cycle.” ([Oracle APT])

<br />

In the last times I have used _APT_ to auto generate Java code in order to improve developers’ productivity and to minimize errors. Since the _APT_ is integrated in the Java compiler, it is activated by default at compile time so no extra effort is required to adopt it in the projects. Moreover modern IDEs, such as Eclipse, have built-in features to support it at development time.
Since the _APT_ works at compile time, all the code (annotations, processors, etc.) needed to activate the _APT_ has to be included in the classpath. Moreover the code used by the _APT_ cannot have direct reference to the source code. At least the source code cannot be modified: only new classes can be created.

In this tutorial I’ll show a simple example about how to use it. In order to generate Java code I’ll use [Code Model].


## Overview
In this tutorial there are two projects, one containing the code needed to activate the _APT_, called `code-generator`, and one representing a hypothetical client project, called `example`.

> Typically the annotations used to activate the _APT_ are included in a third project referred by both the code generator and the client project in order to prevent the client project from having a dependency from the code generator project.
> 
> ![Project' Dependencies](/images/projects_dependencies.png)

The hypothetical client project requires that every entity of the model can be uniquely identified on the basis of one or more of its fields. Moreover when a field is modified a _Journal Monitor_ has to be notified.
The first solution is that every entity class overrides the `equals` and `hashCode` methods and that every `set` method invokes the _Journal Monitor_ to notify it about the update. This approach is error prone and it is expensive to make changes.
The second solution is to automate the process using _APT_ to generate the boiler plate code. As the source code cannot be modified during the _APT_ execution, it is necessary to design the model architecture in order to support this constraint.
The following class diagram describes the model architecture adopted to allow the usage of the _APT_ for code generation.

![Model Class Diagram](/images/model_class_diagram.png)

The `equals` and `hashCode` methods are placed in an external class called _Key_ that extends `AbstractModelKey` and that is auto generated by the _APT_. The `equals` and `hashCode` methods of the `AbstractModel` delegate the operations to the related _Key_ class: the instance of the _Key_ class is created using the [Reflections]. For each entity, an abstract class is created and it is annotated in order to activate _APT_ during the compilation. These abstract classes extend `AbstractModel` and they contain only the attributes as the `set` and `get` methods will be included in the auto generated classes that will extend the abstract entity class. Every `set` method will invoke the `notifyUpdate` method of `AbstractModel`.
Two annotations will be created: `Model` and `ModelId`. The first is used to annotate the abstract entity class and will activate the _APT_, the second is used to annotate the attributes representing the key.


## APT Code Generator Project
The Code Generator project contains the annotations used to activate the APT, the Processor and all the support classes needed to generate the code.

![Code Generator Project Structure](/images/code_generator_project_structure.png)

### Annotations
There are only two annotations, `Model` and `ModelId`.

#### Model
The `Model` annotation is used to activate the APT.
```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface Model {}
```
The `@Retention` annotation specifies the scope of the `Model` annotation. We want the compiler to discard it after the compilation phase so we set it to `SOURCE`. The `@Target` annotation specifies that this annotation can be applied only to `TYPE` elements. In fact we want to apply the `Model` annotation only to classes.

#### ModelId
The `ModelId` annotation is used to generate the _Key_ class. It is not used to activate the _APT_ but it is used during the processing of the `Model` annotation.
```java
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface ModelId {}
```
As well as for the `Model` annotation we don’t want to retain the `ModelId` annotation after the compilation phase so the `@Retention` is set to `SOURCE`. The `@Target` is set to `FIELD` as we want to apply the annotation only to the fields of the models.

### ModelProcessor
The _APT_ needs the _Annotation Processors_ to operate. Each _Processor_ can process more than one annotation. All the annotations a _Processor_ can process are specified by the `@SupportedAnnotationTypes` annotation. Each _Processor_ inherits from `AbstractProcessor` and have to override the `process` method.

The `ModelProcessor` is the _Processor_ used to process the `Model` annotation.
```java
@SupportedAnnotationTypes({
"it.ninjatech.apt.codegenerator.annotation.Model"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ModelProcessor extends AbstractProcessor {}
```
I have overridden the `init` method too, that is invoked by the _APT_ to initialize the _Processor_. In this method I set some helper objects I will use after:

* [Filer](https://docs.oracle.com/javase/8/docs/api/javax/annotation/processing/Filer.html) used to create new files.
* [Messager](https://docs.oracle.com/javase/8/docs/api/javax/annotation/processing/Messager.html) used to report error messages, warnings and other notices.
* [Element Utils](https://docs.oracle.com/javase/8/docs/api/javax/lang/model/util/Elements.html) containing utility methods for operating on program elements.
```java
@Override
public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);

    this.filer = processingEnvironment.getFiler();
    this.messager = processingEnvironment.getMessager();
    this.elementUtils = processingEnvironment.getElementUtils();
}

```
As said, the _Processor_ has to override the `process` method
```java
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
```
In the `process` method I retrieve all the elements annotated by the `Model` annotation and for each element I call the `generate` method to generate the `Model` and the `ModelKey` classes. I use the _[Code Model]_ library to generate the code.

>The classes used by the _APT_ belong to the `javax.lang.model` package and subpackages.

### SPI
To notify _APT_ about _Processors_ it can be used the _Annotation Processing Factories_ or the _Service Provider Interface_ (SPI).
In this tutorial I use SPI. I create a file called `javax.annotation.processing.Processor` located in the `META-INF/services` in which I put the fully qualified name of the processor:
```
it.ninjatech.apt.codegenerator.ModelProcessor
```

### POM
The project uses [Apache Maven] and in order to create a _JAR_ containing all the third party libraries I add the assembly plugin.
```
<plugin>
	<groupId>org.apache.maven.plugins</groupId>
	<artifactId>maven-assembly-plugin</artifactId>
	<version>${assembly-plugin.version}</version>
	<executions>
		<execution>
			<phase>package</phase>
			<goals>
				<goal>single</goal>
			</goals>
		</execution>
	</executions>
	<configuration>
		<descriptorRefs>
			<descriptorRef>jar-with-dependencies</descriptorRef>
		</descriptorRefs>
		<appendAssemblyId>false</appendAssemblyId>
	</configuration>
</plugin>
```
The need to create a single JAR with all the dependencies is to avoid adding several JARs to the classpath.


## APT Example Project
The Example project represents the hypothetical client project.

![Example Project Structure](/images/example_project_structure.png)

In this project there are the `ModelJournalMonitor`, `AbstractModel` and `AbstractModelKey` classes. Moreover there is the `AbstractProduct` class that is the first entity of the project.

### ModelJournalMonitor
The `ModelJournalMonitor` is the journal monitor that has to be notified when an update to an entity takes place. It’s a _Singleton_ and exposes two methods
```java
public static ModelJournalMonitor getInstance()
```
that returns the instance of the journal monitor and
```java
public void notifyUpdate(AbstractModel model)
```
invoked when an entity is updated.

### AbstractModel
`AbstractModel` is the abstract class of the entities.

It exposes only one abstract method
```java
protected abstract String keyQualifiedName();
```
that returns the fully qualified name of the related _Key_ class and it is invoked by the `getKey` method to create a new instance of the _Key_
```java
public final <Model extends AbstractModel> AbstractModelKey<Model> getKey() {
    AbstractModelKey<Model> result = null;

    try {
        result = (AbstractModelKey<Model>)Class.forName(keyQualifiedName()).getConstructor(this.getClass()).newInstance(this);
    }
    catch (Exception e) {
        throw new RuntimeException(String.format("Key construction failed: %s", keyQualifiedName()), e);
    }

    return result;
}
```
It implements the `hashCode` and `equals` methods delegating the execution to the _Key_ class
```java
@Override
public int hashCode() {
    AbstractModelKey<?> key = getKey();
    return key.hashCode();
}

@Override
public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (obj == null) {
       return false;
    }
    if (getClass() != obj.getClass() || !(obj instanceof AbstractModel)) {
        return false;
    }
    AbstractModelKey<?> myKey = getKey();
    AbstractModelKey<?> otherKey = ((AbstractModel)obj).getKey();

    return myKey.equals(otherKey);
}
```
Moreover it contains the `notifyUpdate` method used by the children classes to notify the journal monitor about the update of the entity
```java
protected final void notifyUpdate() {
    ModelJournalMonitor.getInstance().notifyUpdate(this);
}
```

### AbstractModelKey
`AbstractModelKey` is the abstract class of the Keys. It exposes only two abstract methods
```java
@Override
public abstract int hashCode();

@Override
public abstract boolean equals(Object other);
```

### AbstractProduct
`AbstractProduct` is the first entity class. It is annotated with the `Model` annotation in order to activate _APT_ and it contains only two attributes, `id` and `name`. The `id` attribute is annotated with `ModelId` as it is the key of the entity.
```java
@Model
public abstract class AbstractProduct extends AbstractModel {

    @ModelId
    protected Integer id;
    protected String name;
    
}
```
When the _APT_ is executed it produces two classes `Product` and `ProductKey`. The generated classes are saved in the `target/generated-sources/annotations` directory.

>**Note**: Since the generated classes are placed under the `target` directory they are not versioned by a code versioning tool.

#### Product
Following is the generated code of the `Product` class
```java
@Generated(value = "it.ninjatech.apt.example.model.Product", date = "2016-08-29T15:55:35+0200")
public class Product
    extends AbstractProduct
{


    @Override
    public String keyQualifiedName() {
        return "it.ninjatech.apt.example.model.key.ProductKey";
    }

    public Integer getId() {
        return this.id;
    }

    public void setId(Integer id) {
        this.id = id;
        notifyUpdate();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
        notifyUpdate();
    }

}
```
The class overrides the `keyQualifiedName` returning the name of the `ProductKey` class and it contains the `set` and `get` methods of the two attributes, `id` and `name`. The `set` methods invoke the `notifyUpdate` method as expected.

#### ProductKey
Following is the generated code of the `ProductKey` class
```java
@Generated(value = "it.ninjatech.apt.example.model.key.ProductKey", date = "2016-08-29T15:55:35+0200")
public class ProductKey
    extends AbstractModelKey<Product>
{

    private final Integer id;

    public ProductKey(Product model) {
        this.id = model.getId();
    }

    public ProductKey(Integer id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = ((prime*result)+((id == null)? 0 :id.hashCode()));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass()!= obj.getClass()) {
            return false;
        }
        ProductKey other = ((ProductKey) obj);
        if (this.id == null) {
            if (other.id!= null) {
                return false;
            }
        } else {
            if (!this.id.equals(other.id)) {
                return false;
            }
        }
        return true;
    }

    public Integer getId() {
        return this.id;
    }

}
```
It overrides the `hashCode` and `equals` methods evaluating the result on the base of the _Product_ `id` attribute, as expected.


## Integration with Eclipse
Since the _APT_ is integrated into the Java compiler it can be used by Eclipse too. In fact, Eclipse, has an option to enable annotation processing.
In the project properties, under _Java Compiler_, there is the _Annotation Processing_ settings pane.

![Annotation Processing Settings Pane](/images/annotation_processing_settings_pane.png)

To add our code generator we have to enable the project specific settings in the _Factory Path_ pane and add the code generator JAR to the _Plug-ins and JARs that contain annotation processors_ list

![Factory Path Settings Pane](/images/factory_path_settings_pane.png)

Now we can enable the project specific settings in the _Annotation Processing_ pane, enable annotation processing and set the generated source directory to `/target/generated-sources/annotations`.

![Annotation Processing Settings Pane](/images/annotation_processing_settings_pane_2.png)

Applying the new settings the project structure changes and now Eclipse shows the generated classes.

![Changed Example Project Structure](/images/changed_example_project_structure.png)

It is possible to access to the messages published by the `Messager` class opening the _Error Log_

![Eclipse Error Log](/images/eclipse_error_log.png)

At this point every time the `AbstractProduct` class is saved the generated code is produced.

## Conclusion
The _APT_ is a powerful tool to automate some development tasks. It is integrated into the Java compiler so it requires no extra effort to adopt it. There are many situations in which _APT_ can be used, especially when there are much boiler plate code. Moreover the main IDEs are ready to take advantage of it.


## License
Released and distributed under the [Apache License Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

## References
[Apache Maven][] 
[Code Model][]
[Oracle APT][]
[Reflections][]

[apache maven]: https://maven.apache.org/
[code model]: https://codemodel.java.net/
[oracle apt]: http://docs.oracle.com/javase/7/docs/technotes/guides/apt/
[reflections]: http://www.oracle.com/technetwork/articles/java/javareflection-1536171.html

[abstractmodel]: #abstractmodel
[abstractmodelkey]: #abstractmodelkey
[abstractproduct]: #abstractproduct
[annotations]: #annotations
[apt code generator project]: #apt-code-generator-project
[apt example project]: #apt-example-project
[conclusion]: #conclusion
[integration with eclipse]: #integration-with-eclipse
[introduction]: #introduction
[license]: #license
[model]: #model
[modelid]: modelid
[modeljournalmonitor]: #modeljournalmonitor
[modelprocessor]: #modelprocessor
[overview]: #overview
[pom]: #pom
[product]: #product
[productkey]: #productkey
[references]: #references
[spi]: #spi
