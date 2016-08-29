package it.ninjatech.apt.codegenerator;

public final class FieldInfo {

    private final String type;
    private final String name;
    
    protected FieldInfo(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }
    
}
