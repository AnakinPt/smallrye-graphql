package io.smallrye.graphql.schema.creator;

import java.lang.reflect.Modifier;
import java.util.Optional;

import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.smallrye.graphql.schema.Annotations;
import io.smallrye.graphql.schema.Classes;
import io.smallrye.graphql.schema.SchemaBuilderException;
import io.smallrye.graphql.schema.helper.DefaultValueHelper;
import io.smallrye.graphql.schema.helper.DescriptionHelper;
import io.smallrye.graphql.schema.helper.Direction;
import io.smallrye.graphql.schema.helper.Directives;
import io.smallrye.graphql.schema.helper.FormatHelper;
import io.smallrye.graphql.schema.helper.IgnoreHelper;
import io.smallrye.graphql.schema.helper.MappingHelper;
import io.smallrye.graphql.schema.helper.MethodHelper;
import io.smallrye.graphql.schema.helper.NonNullHelper;
import io.smallrye.graphql.schema.model.Field;
import io.smallrye.graphql.schema.model.Reference;

/**
 * Creates a Field object
 *
 * @author Phillip Kruger (phillip.kruger@redhat.com)
 */
public class FieldCreator {

    private final ReferenceCreator referenceCreator;
    private Directives directives;

    public FieldCreator(ReferenceCreator referenceCreator) {
        this.referenceCreator = referenceCreator;
    }

    public void setDirectives(Directives directives) {
        this.directives = directives;
    }

    /**
     * Creates a field from a method only.
     * This is used in the case of an interface
     *
     * @param methodInfo the java method
     * @return a Field model object
     */
    public Optional<Field> createFieldForInterface(MethodInfo methodInfo, Reference parentObjectReference) {
        Annotations annotationsForMethod = Annotations.getAnnotationsForInterfaceField(methodInfo);

        if (!isGraphQlField(Direction.OUT, null, methodInfo)) {
            return Optional.empty();
        }

        Type returnType = methodInfo.returnType();

        // Name
        String name = getFieldName(Direction.OUT, annotationsForMethod, methodInfo.name());

        // Field Type
        validateFieldType(Direction.OUT, methodInfo);
        Reference reference = referenceCreator.createReferenceForInterfaceField(returnType, annotationsForMethod,
                parentObjectReference);

        Field field = new Field(methodInfo.name(), MethodHelper.getPropertyName(Direction.OUT, methodInfo.name()), name,
                reference);
        configure(field, returnType, annotationsForMethod);

        return Optional.of(field);
    }

    public void configure(Field field, Type type, Annotations annotations) {
        // Wrapper
        field.setWrapper(WrapperCreator.createWrapper(type).orElse(null));

        configure2(field, type, annotations);
    }

    private void configure2(Field field, Type type, Annotations annotations) {
        // Description
        DescriptionHelper.getDescriptionForField(annotations, type).ifPresent(field::setDescription);

        // NotNull
        if (NonNullHelper.markAsNonNull(type, annotations)) {
            field.setNotNull(true);
        }

        // TransformInfo
        field.setTransformation(FormatHelper.getFormat(type, annotations).orElse(null));

        // MappingInfo
        field.setMapping(MappingHelper.getMapping(field, annotations).orElse(null));

        // Default Value
        field.setDefaultValue(DefaultValueHelper.getDefaultValue(annotations).orElse(null));

        // Directives
        if (directives != null) { // this happens while scanning for the directive types
            field.setDirectiveInstances(
                    directives.buildDirectiveInstances(name -> annotations.getOneOfTheseAnnotations(name).orElse(null)));
        }
    }

    /**
     * Creates a field from a field and method.
     * Used by Type and Input
     *
     * @param direction the direction (in/out)
     * @param fieldInfo the java property
     * @param methodInfo the java method
     * @return a Field model object
     */
    public Optional<Field> createFieldForPojo(Direction direction, FieldInfo fieldInfo, MethodInfo methodInfo,
            Reference parentObjectReference) {
        Annotations annotationsForPojo = Annotations.getAnnotationsForPojo(direction, fieldInfo, methodInfo);

        if (isGraphQlField(direction, fieldInfo, methodInfo)) {
            Type methodType = getMethodType(methodInfo, direction);

            // Name
            String name = getFieldName(direction, annotationsForPojo, methodInfo.name());

            // Field Type
            validateFieldType(direction, methodInfo);
            Type fieldType = getFieldType(fieldInfo, methodType);

            Reference reference = referenceCreator.createReferenceForPojoField(direction, fieldType, methodType,
                    annotationsForPojo, parentObjectReference);

            Field field = new Field(methodInfo.name(), MethodHelper.getPropertyName(direction, methodInfo.name()), name,
                    reference);

            // Wrapper
            field.setWrapper(WrapperCreator.createWrapper(fieldType, methodType).orElse(null));

            configure2(field, methodType, annotationsForPojo);

            return Optional.of(field);
        }
        return Optional.empty();
    }

    public Optional<Field> createFieldForParameter(MethodInfo method, short position, FieldInfo fieldInfo,
            Reference parentObjectReference) {
        Annotations annotationsForPojo = Annotations.getAnnotationsForInputCreator(method, position, fieldInfo);

        // Name
        String name = getFieldName(Direction.IN, annotationsForPojo, method.parameterName(position));

        // Field Type
        Type fieldType = getFieldType(fieldInfo, method.parameters().get(position));

        Reference reference = referenceCreator.createReferenceForPojoField(Direction.IN, fieldType,
                method.parameters().get(position), annotationsForPojo, parentObjectReference);

        String fieldName = fieldInfo != null ? fieldInfo.name() : null;
        Field field = new Field(null, fieldName, name, reference);

        // Wrapper
        field.setWrapper(WrapperCreator.createWrapper(fieldType, method.parameters().get(position)).orElse(null));

        configure2(field, method.parameters().get(position), annotationsForPojo);

        return Optional.of(field);

    }

    /**
     * Creates a field from a public field.
     * Used by Type and Input
     *
     * @param direction the direction (in/out)
     * @param fieldInfo the java property
     * @return a Field model object
     */
    public Optional<Field> createFieldForPojo(Direction direction, FieldInfo fieldInfo, Reference parentObjectReference) {
        if (isGraphQlField(direction, fieldInfo, null)) {
            Annotations annotationsForPojo = Annotations.getAnnotationsForPojo(direction, fieldInfo);

            // Name
            String name = getFieldName(direction, annotationsForPojo, fieldInfo.name());

            // Field Type
            Type fieldType = fieldInfo.type();

            Reference reference = referenceCreator.createReferenceForPojoField(direction, fieldType, fieldType,
                    annotationsForPojo, parentObjectReference);

            Field field = new Field(null,
                    fieldInfo.name(),
                    name,
                    reference);

            configure(field, fieldType, annotationsForPojo);

            return Optional.of(field);
        }
        return Optional.empty();
    }

    /**
     * Checks if method and/or field are useable as a GraphQL-Field.
     *
     * @param direction the direction, IN if the field should be used on an input type, OUT otherwise
     * @param fieldInfo the field. If null, methodInfo must be provided
     * @param methodInfo the method. If null, fieldInfo must be provided
     * @return if it is an GraphQL field
     */
    protected static boolean isGraphQlField(Direction direction, FieldInfo fieldInfo, MethodInfo methodInfo) {
        boolean methodAccessible = isPossibleField(methodInfo);
        boolean fieldAccessible = isPossibleField(direction, fieldInfo);

        if (!methodAccessible && !fieldAccessible) {
            return false;
        }

        Annotations annotationsForPojo = Annotations.getAnnotationsForPojo(direction, fieldInfo, methodInfo);
        return !IgnoreHelper.shouldIgnore(annotationsForPojo, fieldInfo);
    }

    /**
     * Checks if the method is a possible GraphQl field (by method access).
     * This means that the method:
     * <ul>
     * </ul>
     * <li>exists</li>
     * <li>is public</li>
     * <li>and is not static</li>
     * </ul>
     *
     * @param methodInfo the method
     * @return if the method is a possible GraphQl field
     */
    private static boolean isPossibleField(MethodInfo methodInfo) {
        return methodInfo != null
                && Modifier.isPublic(methodInfo.flags())
                && !Modifier.isStatic(methodInfo.flags());
    }

    /**
     * Checks if the field is a possible GraphQl field (by field access).
     *
     * This means that the field:
     * <ul>
     * </ul>
     * <li>exists</li>
     * <li>is public</li>
     * <li>is not static</li>
     * <li>and is not final, if it's an input field</li>
     * </ul>
     *
     * @param direction the direction
     * @param fieldInfo the method
     * @return if the field is a possible GraphQl field
     */
    private static boolean isPossibleField(Direction direction, FieldInfo fieldInfo) {
        return fieldInfo != null
                && !(direction == Direction.IN && Modifier.isFinal(fieldInfo.flags()))
                && Modifier.isPublic(fieldInfo.flags())
                && !Modifier.isStatic(fieldInfo.flags());
    }

    private static void validateFieldType(Direction direction, MethodInfo methodInfo) {
        Type returnType = methodInfo.returnType();
        if (direction.equals(Direction.OUT) && returnType.kind().equals(Type.Kind.VOID)) {
            throw new SchemaBuilderException(
                    "Can not have a void return method [" + methodInfo.name() + "] in class ["
                            + methodInfo.declaringClass().name() + "]");
        }
    }

    private static Type getMethodType(MethodInfo method, Direction direction) {
        if (direction.equals(Direction.IN)) {
            return method.parameters().get(0);
        }
        return method.returnType();
    }

    private static Type getFieldType(FieldInfo fieldInfo, Type defaultType) {
        if (fieldInfo == null || fieldInfo.type().name().equals(Classes.SERIALIZABLE)
                || fieldInfo.type().name().equals(Classes.OBJECT)) {
            return defaultType;
        }
        return fieldInfo.type();
    }

    /**
     * Get the field name. Depending on the direction, we either also look at getter/setters
     *
     * @param direction the direction
     * @param annotationsForThisField annotations on this field
     * @param defaultFieldName the default field name
     * @return the field name
     */
    static String getFieldName(Direction direction, Annotations annotationsForThisField,
            String defaultFieldName) {
        switch (direction) {
            case OUT:
                return getOutputNameForField(annotationsForThisField, defaultFieldName);
            case IN:
                return getInputNameForField(annotationsForThisField, defaultFieldName);
            default:
                return defaultFieldName;
        }
    }

    private static String getOutputNameForField(Annotations annotationsForThisField, String fieldName) {
        return annotationsForThisField.getOneOfTheseMethodAnnotationsValue(
                Annotations.NAME,
                Annotations.QUERY,
                Annotations.JSONB_PROPERTY,
                Annotations.JACKSON_PROPERTY)
                .orElse(annotationsForThisField.getOneOfTheseAnnotationsValue(
                        Annotations.NAME,
                        Annotations.QUERY,
                        Annotations.JSONB_PROPERTY,
                        Annotations.JACKSON_PROPERTY)
                        .orElse(MethodHelper.getPropertyName(Direction.OUT, fieldName)));
    }

    private static String getInputNameForField(Annotations annotationsForThisField, String fieldName) {
        return annotationsForThisField.getOneOfTheseMethodAnnotationsValue(
                Annotations.NAME,
                Annotations.JSONB_PROPERTY,
                Annotations.JACKSON_PROPERTY)
                .orElse(annotationsForThisField.getOneOfTheseAnnotationsValue(
                        Annotations.NAME,
                        Annotations.JSONB_PROPERTY,
                        Annotations.JACKSON_PROPERTY)
                        .orElse(MethodHelper.getPropertyName(Direction.IN, fieldName)));
    }
}
