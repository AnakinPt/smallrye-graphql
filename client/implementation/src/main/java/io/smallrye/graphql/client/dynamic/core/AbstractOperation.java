package io.smallrye.graphql.client.dynamic.core;

import java.util.List;

import io.smallrye.graphql.client.core.FieldOrFragment;
import io.smallrye.graphql.client.core.Operation;
import io.smallrye.graphql.client.core.OperationType;
import io.smallrye.graphql.client.core.Variable;

public abstract class AbstractOperation implements Operation {
    private OperationType type;
    private String name;
    private List<Variable> variables;
    private List<FieldOrFragment> fields;

    /*
     * Constructors
     */
    public AbstractOperation() {
    }

    /*
     * Getter/Setter
     */
    public OperationType getType() {
        return type;
    }

    public void setType(OperationType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Variable> getVariables() {
        return variables;
    }

    public void setVariables(List<Variable> vars) {
        this.variables = vars;
    }

    public List<FieldOrFragment> getFields() {
        return fields;
    }

    public void setFields(List<FieldOrFragment> fields) {
        this.fields = fields;
    }
}
