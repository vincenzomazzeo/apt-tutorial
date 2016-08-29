package it.ninjatech.apt.example;

public abstract class AbstractModelKey<Model extends AbstractModel> {

    @Override
    public abstract int hashCode();
    
    @Override
    public abstract boolean equals(Object other);
    
}
