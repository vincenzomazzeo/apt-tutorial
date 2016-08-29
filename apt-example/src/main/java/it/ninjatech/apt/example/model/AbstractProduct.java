package it.ninjatech.apt.example.model;

import it.ninjatech.apt.codegenerator.annotation.Model;
import it.ninjatech.apt.codegenerator.annotation.ModelId;
import it.ninjatech.apt.example.AbstractModel;

@Model
public abstract class AbstractProduct extends AbstractModel {

    @ModelId
    protected Integer id;
    protected String name;
    
}
