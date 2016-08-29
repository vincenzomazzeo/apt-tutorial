package it.ninjatech.apt.example;

public abstract class AbstractModel {

    protected abstract String keyQualifiedName();
 
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

    @SuppressWarnings("unchecked")
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
    
    protected final void notifyUpdate() {
        ModelJournalMonitor.getInstance().notifyUpdate(this);
    }
    
}
