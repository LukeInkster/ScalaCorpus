/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.data;

import javax.validation.*;
import javax.validation.metadata.*;
import javax.validation.groups.Default;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.lang.annotation.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.mvc.Http;

import static play.libs.F.*;

import play.data.validation.*;
import play.data.format.Formatters;

import org.springframework.beans.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.*;
import org.springframework.validation.beanvalidation.*;
import org.springframework.context.support.*;

import com.google.common.collect.ImmutableList;

/**
 * Helper to manage HTML form description, submission and validation.
 */
public class Form<T> {

    /**
     * Defines a form element's display name.
     */
    @Retention(RUNTIME)
    @Target({ANNOTATION_TYPE})
    public @interface Display {
        String name();
        String[] attributes() default {};
    }

    // --

    private final String rootName;
    private final Class<T> backedType;
    private final Map<String,String> data;
    private final Map<String,List<ValidationError>> errors;
    private final Optional<T> value;
    private final Class<?>[] groups;
    final MessagesApi messagesApi;
    final Formatters formatters;
    final javax.validation.Validator validator;

    public Class<T> getBackedType() {
        return backedType;
    }

    protected T blankInstance() {
        try {
            return backedType.newInstance();
        } catch(Exception e) {
            throw new RuntimeException("Cannot instantiate " + backedType + ". It must have a default constructor", e);
        }
    }

    /**
     * Creates a new <code>Form</code>.
     *
     * @param clazz wrapped class
     */
    public Form(Class<T> clazz, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(null, clazz, messagesApi, formatters, validator);
    }

    public Form(String rootName, Class<T> clazz, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, (Class<?>)null, messagesApi, formatters, validator);
    }

    public Form(String rootName, Class<T> clazz, Class<?> group, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, group != null ? new Class[]{group} : null, messagesApi, formatters, validator);
    }

    public Form(String rootName, Class<T> clazz, Class<?>[] groups, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, new HashMap<>(), new HashMap<>(), Optional.empty(), groups, messagesApi, formatters, validator);
    }

    public Form(String rootName, Class<T> clazz, Map<String,String> data, Map<String,List<ValidationError>> errors, Optional<T> value, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, data, errors, value, (Class<?>)null, messagesApi, formatters, validator);
    }

    public Form(String rootName, Class<T> clazz, Map<String,String> data, Map<String,List<ValidationError>> errors, Optional<T> value, Class<?> group, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this(rootName, clazz, data, errors, value, group != null ? new Class[]{group} : null, messagesApi, formatters, validator);
    }

    /**
     * Creates a new <code>Form</code>.
     *
     * @param clazz wrapped class
     * @param data the current form data (used to display the form)
     * @param errors the collection of errors associated with this form
     * @param value optional concrete value of type <code>T</code> if the form submission was successful
     * @param messagesApi needed to look up various messages
     * @param formatters used for parsing and printing form fields
     */
    public Form(String rootName, Class<T> clazz, Map<String,String> data, Map<String,List<ValidationError>> errors, Optional<T> value, Class<?>[] groups, MessagesApi messagesApi, Formatters formatters, javax.validation.Validator validator) {
        this.rootName = rootName;
        this.backedType = clazz;
        this.data = data;
        this.errors = errors;
        this.value = value;
        this.groups = groups;
        this.messagesApi = messagesApi;
        this.formatters = formatters;
        this.validator = validator;
    }

    protected Map<String,String> requestData(Http.Request request) {

        Map<String,String[]> urlFormEncoded = new HashMap<>();
        if (request.body().asFormUrlEncoded() != null) {
            urlFormEncoded = request.body().asFormUrlEncoded();
        }

        Map<String,String[]> multipartFormData = new HashMap<>();
        if (request.body().asMultipartFormData() != null) {
            multipartFormData = request.body().asMultipartFormData().asFormUrlEncoded();
        }

        Map<String,String> jsonData = new HashMap<>();
        if (request.body().asJson() != null) {
            jsonData = play.libs.Scala.asJava(
                play.api.data.FormUtils.fromJson("",
                    play.api.libs.json.Json.parse(
                        play.libs.Json.stringify(request.body().asJson())
                    )
                )
            );
        }

        Map<String,String[]> queryString = request.queryString();

        Map<String,String> data = new HashMap<>();

        fillDataWith(data, urlFormEncoded);
        fillDataWith(data, multipartFormData);

        jsonData.forEach(data::put);

        fillDataWith(data, queryString);

        return data;
    }

    private void fillDataWith(Map<String, String> data, Map<String, String[]> urlFormEncoded) {
        urlFormEncoded.forEach((key, values) -> {
            if (key.endsWith("[]")) {
                String k = key.substring(0, key.length() - 2);
                for (int i = 0; i < values.length; i++) {
                    data.put(k + "[" + i + "]", values[i]);
                }
            } else if (values.length > 0) {
                data.put(key, values[0]);
            }
        });
    }

    /**
     * Binds request data to this form - that is, handles form submission.
     *
     * @return a copy of this form filled with the new data
     */
    public Form<T> bindFromRequest(String... allowedFields) {
        return bind(requestData(play.mvc.Controller.request()), allowedFields);
    }

    /**
     * Binds request data to this form - that is, handles form submission.
     *
     * @return a copy of this form filled with the new data
     */
    public Form<T> bindFromRequest(Http.Request request, String... allowedFields) {
        return bind(requestData(request), allowedFields);
    }

    /**
     * Binds request data to this form - that is, handles form submission.
     *
     * @return a copy of this form filled with the new data
     */
    public Form<T> bindFromRequest(Map<String,String[]> requestData, String... allowedFields) {
        Map<String,String> data = new HashMap<>();
        fillDataWith(data, requestData);
        return bind(data, allowedFields);
    }

    /**
     * Binds Json data to this form - that is, handles form submission.
     *
     * @param data data to submit
     * @return a copy of this form filled with the new data
     */
    public Form<T> bind(com.fasterxml.jackson.databind.JsonNode data, String... allowedFields) {
        return bind(
            play.libs.Scala.asJava(
                play.api.data.FormUtils.fromJson("",
                    play.api.libs.json.Json.parse(
                        play.libs.Json.stringify(data)
                    )
                )
            ),
            allowedFields
        );
    }

    private static final Set<String> internalAnnotationAttributes = new HashSet<>(3);
    static {
        internalAnnotationAttributes.add("message");
        internalAnnotationAttributes.add("groups");
        internalAnnotationAttributes.add("payload");
    }

    protected Object[] getArgumentsForConstraint(String objectName, String field, ConstraintDescriptor<?> descriptor) {
        List<Object> arguments = new LinkedList<>();
        String[] codes = new String[] {objectName + Errors.NESTED_PATH_SEPARATOR + field, field};
        arguments.add(new DefaultMessageSourceResolvable(codes, field));
        // Using a TreeMap for alphabetical ordering of attribute names
        Map<String, Object> attributesToExpose = new TreeMap<>();
        descriptor.getAttributes().forEach((attributeName, attributeValue) -> {
            if (!internalAnnotationAttributes.contains(attributeName)) {
                attributesToExpose.put(attributeName, attributeValue);
            }
        });
        arguments.addAll(attributesToExpose.values());
        return arguments.toArray(new Object[arguments.size()]);
    }

    /**
     * When dealing with @ValidateWith annotations, and message parameter is not used in
     * the annotation, extract the message from validator's getErrorMessageKey() method
    **/
    protected String getMessageForConstraintViolation(ConstraintViolation<Object> violation) {
        String errorMessage = violation.getMessage();
        Annotation annotation = violation.getConstraintDescriptor().getAnnotation();
        if (annotation instanceof Constraints.ValidateWith) {
            Constraints.ValidateWith validateWithAnnotation = (Constraints.ValidateWith)annotation;
            if (violation.getMessage().equals(Constraints.ValidateWithValidator.defaultMessage)) {
                Constraints.ValidateWithValidator validateWithValidator = new Constraints.ValidateWithValidator();
                validateWithValidator.initialize(validateWithAnnotation);
                Tuple<String, Object[]> errorMessageKey = validateWithValidator.getErrorMessageKey();
                if (errorMessageKey != null && errorMessageKey._1 != null) {
                    errorMessage = errorMessageKey._1;
                }
            }
        }

        return errorMessage;
    }

    /**
     * Binds data to this form - that is, handles form submission.
     *
     * @param data data to submit
     * @return a copy of this form filled with the new data
     */
    @SuppressWarnings("unchecked")
    public Form<T> bind(Map<String,String> data, String... allowedFields) {

        DataBinder dataBinder;
        Map<String, String> objectData = data;
        if (rootName == null) {
            dataBinder = new DataBinder(blankInstance());
        } else {
            dataBinder = new DataBinder(blankInstance(), rootName);
            objectData = new HashMap<>();
            for (String key: data.keySet()) {
                if (key.startsWith(rootName + ".")) {
                    objectData.put(key.substring(rootName.length() + 1), data.get(key));
                }
            }
        }
        if (allowedFields.length > 0) {
            dataBinder.setAllowedFields(allowedFields);
        }
        SpringValidatorAdapter validator = new SpringValidatorAdapter(this.validator);
        dataBinder.setValidator(validator);
        dataBinder.setConversionService(formatters.conversion);
        dataBinder.setAutoGrowNestedPaths(true);
        final Map<String, String> objectDataFinal = objectData;
        withRequestLocale(() -> { dataBinder.bind(new MutablePropertyValues(objectDataFinal)); return null; });
        Set<ConstraintViolation<Object>> validationErrors;
        if (groups != null) {
            validationErrors = validator.validate(dataBinder.getTarget(), groups);
        } else {
            validationErrors = validator.validate(dataBinder.getTarget());
        }

        BindingResult result = dataBinder.getBindingResult();

        for (ConstraintViolation<Object> violation : validationErrors) {
            String field = violation.getPropertyPath().toString();
            FieldError fieldError = result.getFieldError(field);
            if (fieldError == null || !fieldError.isBindingFailure()) {
                try {
                    result.rejectValue(field,
                            violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                            getArgumentsForConstraint(result.getObjectName(), field, violation.getConstraintDescriptor()),
                            getMessageForConstraintViolation(violation));
                } catch (NotReadablePropertyException ex) {
                    throw new IllegalStateException("JSR-303 validated property '" + field +
                            "' does not have a corresponding accessor for data binding - " +
                            "check your DataBinder's configuration (bean property versus direct field access)", ex);
                }
            }
        }

        if (result.hasErrors() || result.getGlobalErrorCount() > 0) {
            Map<String,List<ValidationError>> errors = new HashMap<>();
            for (FieldError error: result.getFieldErrors()) {
                String key = error.getObjectName() + "." + error.getField();
                if (key.startsWith("target.") && rootName == null) {
                    key = key.substring(7);
                }
                if (!errors.containsKey(key)) {
                   errors.put(key, new ArrayList<>());
                }

                ValidationError validationError;
                if (error.isBindingFailure()) {
                    ImmutableList.Builder<String> builder = ImmutableList.builder();
                    Optional<Messages> msgs = Optional.ofNullable(Http.Context.current.get()).map(Http.Context::messages);
                    for (String code: error.getCodes()) {
                        code = code.replace("typeMismatch", "error.invalid");
                        if (!msgs.isPresent() || msgs.get().isDefinedAt(code)) {
                            builder.add( code );
                        }
                    }
                    validationError = new ValidationError(key, builder.build().reverse(),
                            convertErrorArguments(error.getArguments()));
                } else {
                    validationError = new ValidationError(key, error.getDefaultMessage(),
                            convertErrorArguments(error.getArguments()));
                }
                errors.get(key).add(validationError);
            }

            List<ValidationError> globalErrors = result.getGlobalErrors().stream()
                    .map(error -> new ValidationError("", error.getDefaultMessage(), convertErrorArguments(error.getArguments())))
                    .collect(Collectors.toList());

            if (!globalErrors.isEmpty()) {
                errors.put("", globalErrors);
            }

            return new Form(rootName, backedType, data, errors, Optional.empty(), groups, messagesApi, formatters, this.validator);
        } else {
            Object globalError = null;
            if (result.getTarget() != null) {
                try {
                    java.lang.reflect.Method v = result.getTarget().getClass().getMethod("validate");
                    globalError = v.invoke(result.getTarget());
                } catch (NoSuchMethodException e) {
                    // do nothing
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
            if (globalError != null) {
                Map<String,List<ValidationError>> errors = new HashMap<>();
                if (globalError instanceof String) {
                    errors.put("", new ArrayList<>());
                    errors.get("").add(new ValidationError("", (String)globalError, new ArrayList()));
                } else if (globalError instanceof List) {
                    for (ValidationError error : (List<ValidationError>) globalError) {
                      List<ValidationError> errorsForKey = errors.get(error.key());
                      if (errorsForKey == null) {
                        errors.put(error.key(), errorsForKey = new ArrayList<>());
                      }
                      errorsForKey.add(error);
                    }
                } else if (globalError instanceof Map) {
                    errors = (Map<String,List<ValidationError>>)globalError;
                }
                return new Form(rootName, backedType, data, errors, Optional.empty(), groups, messagesApi, formatters, this.validator);
            }
            return new Form(rootName, backedType, new HashMap<>(data), new HashMap<>(errors), Optional.ofNullable((T)result.getTarget()), groups, messagesApi, formatters, this.validator);
        }
    }

    /**
     * Convert the error arguments.
     *
     * @param arguments The arguments to convert.
     * @return The converted arguments.
     */
    private List<Object> convertErrorArguments(Object[] arguments) {
        List<Object> converted = Arrays.stream(arguments)
                .filter(arg -> !(arg instanceof org.springframework.context.support.DefaultMessageSourceResolvable))
                .collect(Collectors.toList());
        return Collections.unmodifiableList(converted);
    }

    /**
     * Retrieves the actual form data.
     */
    public Map<String,String> data() {
        return data;
    }

    public String name() {
        return rootName;
    }

    /**
     * Retrieves the actual form value.
     */
    public Optional<T> value() {
        return value;
    }

    /**
     * Populates this form with an existing value, used for edit forms.
     *
     * @param value existing value of type <code>T</code> used to fill this form
     * @return a copy of this form filled with the new data
     */
    @SuppressWarnings("unchecked")
    public Form<T> fill(T value) {
        if (value == null) {
            throw new RuntimeException("Cannot fill a form with a null value");
        }
        return new Form(
                rootName,
                backedType,
                new HashMap<>(),
                new HashMap<String,ValidationError>(),
                Optional.ofNullable(value),
                groups,
                messagesApi,
                formatters,
                validator
        );
    }

    /**
     * Returns <code>true</code> if there are any errors related to this form.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Returns <code>true</code> if there any global errors related to this form.
     */
    public boolean hasGlobalErrors() {
        return errors.containsKey("") && !errors.get("").isEmpty();
    }

    /**
     * Retrieve all global errors - errors without a key.
     *
     * @return All global errors.
     */
    public List<ValidationError> globalErrors() {
        List<ValidationError> e = errors.get("");
        if (e == null) {
            e = new ArrayList<>();
        }
        return e;
    }

    /**
     * Retrieves the first global error (an error without any key), if it exists.
     *
     * @return An error or <code>null</code>.
     */
    public ValidationError globalError() {
        List<ValidationError> errors = globalErrors();
        if (errors.isEmpty()) {
            return null;
        }
        return errors.get(0);
    }

    /**
     * Returns all errors.
     *
     * @return All errors associated with this form.
     */
    public Map<String,List<ValidationError>> errors() {
        return errors;
    }

    /**
     * Retrieve an error by key.
     */
    public ValidationError error(String key) {
        List<ValidationError> err = errors.get(key);
        if (err == null || err.isEmpty()) {
            return null;
        }
        return err.get(0);
    }

    /**
     * Returns the form errors serialized as Json.
     */
    public com.fasterxml.jackson.databind.JsonNode errorsAsJson() {
        return errorsAsJson(Http.Context.current() != null ? Http.Context.current().lang() : null);
    }

    /**
     * Returns the form errors serialized as Json using the given Lang.
     */
    public com.fasterxml.jackson.databind.JsonNode errorsAsJson(play.i18n.Lang lang) {
        Map<String, List<String>> allMessages = new HashMap<>();
        errors.forEach((key, errs) -> {
            if (errs != null && !errs.isEmpty()) {
                List<String> messages = new ArrayList<>();
                for (ValidationError error : errs) {
                    if (messagesApi != null && lang != null) {
                        messages.add(messagesApi.get(lang, error.messages(), translateMsgArg(error.arguments(), messagesApi, lang)));
                    } else {
                        messages.add(error.message());
                    }
                }
                allMessages.put(key, messages);
            }
        });
        return play.libs.Json.toJson(allMessages);
    }

    private Object translateMsgArg(List<Object> arguments, MessagesApi messagesApi, play.i18n.Lang lang) {
        if (arguments != null) {
            return arguments.stream().map(arg -> {
                    if (arg instanceof String) {
                        return messagesApi != null ? messagesApi.get(lang, (String)arg) : (String)arg;
                    }
                    if (arg instanceof List) {
                        return ((List<?>) arg).stream().map(key -> messagesApi != null ? messagesApi.get(lang, (String)key) : (String)key).collect(Collectors.toList());
                    }
                    return arg;
                }).collect(Collectors.toList());
        } else {
            return null;
       }
    }

    /**
     * Gets the concrete value if the submission was a success.
     *
     * @throws IllegalStateException if there are errors binding the form, including the errors as JSON in the message
     */
    public T get() {
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Error(s) binding form: " + errorsAsJson());
        }
        return value.get();
    }

    /**
     * Adds an error to this form.
     *
     * @param error the <code>ValidationError</code> to add.
     */
    public void reject(ValidationError error) {
        if (!errors.containsKey(error.key())) {
           errors.put(error.key(), new ArrayList<>());
        }
        errors.get(error.key()).add(error);
    }

    /**
     * Adds an error to this form.
     *
     * @param key the error key
     * @param error the error message
     * @param args the error arguments
     */
    public void reject(String key, String error, List<Object> args) {
        reject(new ValidationError(key, error, args));
    }

    /**
     * Adds an error to this form.
     *
     * @param key the error key
     * @param error the error message
     */
    public void reject(String key, String error) {
        reject(key, error, new ArrayList<>());
    }

    /**
     * Adds a global error to this form.
     *
     * @param error the error message
     * @param args the errot arguments
     */
    public void reject(String error, List<Object> args) {
        reject(new ValidationError("", error, args));
    }

    /**
     * Add a global error to this form.
     *
     * @param error the error message.
     */
    public void reject(String error) {
        reject("", error, new ArrayList<>());
    }

    /**
     * Discard errors of this form
     */
    public void discardErrors() {
        errors.clear();
    }

    /**
     * Retrieve a field.
     *
     * @param key field name
     * @return the field (even if the field does not exist you get a field)
     */
    public Field apply(String key) {
        return field(key);
    }

    /**
     * Retrieve a field.
     *
     * @param key field name
     * @return the field (even if the field does not exist you get a field)
     */
    public Field field(String key) {

        // Value
        String fieldValue = null;
        if (data.containsKey(key)) {
            fieldValue = data.get(key);
        } else {
            if (value.isPresent()) {
                BeanWrapper beanWrapper = new BeanWrapperImpl(value.get());
                beanWrapper.setAutoGrowNestedPaths(true);
                String objectKey = key;
                if (rootName != null && key.startsWith(rootName + ".")) {
                    objectKey = key.substring(rootName.length() + 1);
                }
                if (beanWrapper.isReadableProperty(objectKey)) {
                    Object oValue = beanWrapper.getPropertyValue(objectKey);
                    if (oValue != null) {
                        if(formatters != null) {
                            final String objectKeyFinal = objectKey;
                            fieldValue = withRequestLocale(() -> formatters.print(beanWrapper.getPropertyTypeDescriptor(objectKeyFinal), oValue));
                        } else {
                            fieldValue = oValue.toString();
                        }
                    }
                }
            }
        }

        // Error
        List<ValidationError> fieldErrors = errors.get(key);
        if (fieldErrors == null) {
            fieldErrors = new ArrayList<>();
        }

        // Format
        Tuple<String,List<Object>> format = null;
        BeanWrapper beanWrapper = new BeanWrapperImpl(blankInstance());
        beanWrapper.setAutoGrowNestedPaths(true);
        try {
            for (Annotation a: beanWrapper.getPropertyTypeDescriptor(key).getAnnotations()) {
                Class<?> annotationType = a.annotationType();
                if (annotationType.isAnnotationPresent(play.data.Form.Display.class)) {
                    play.data.Form.Display d = annotationType.getAnnotation(play.data.Form.Display.class);
                    if (d.name().startsWith("format.")) {
                        List<Object> attributes = new ArrayList<>();
                        for (String attr: d.attributes()) {
                            Object attrValue = null;
                            try {
                                attrValue = a.getClass().getDeclaredMethod(attr).invoke(a);
                            } catch(Exception e) {
                                // do nothing
                            }
                            attributes.add(attrValue);
                        }
                        format = Tuple(d.name(), attributes);
                    }
                }
            }
        } catch(NullPointerException e) {
            // do nothing
        }

        // Constraints
        List<Tuple<String,List<Object>>> constraints = new ArrayList<>();
        Class<?> classType = backedType;
        String leafKey = key;
        if (rootName != null && leafKey.startsWith(rootName + ".")) {
            leafKey = leafKey.substring(rootName.length() + 1);
        }
        int p = leafKey.lastIndexOf('.');
        if (p > 0) {
            classType = beanWrapper.getPropertyType(leafKey.substring(0, p));
            leafKey = leafKey.substring(p + 1);
        }
        if (classType != null && this.validator != null) {
            BeanDescriptor beanDescriptor = this.validator.getConstraintsForClass(classType);
            if (beanDescriptor != null) {
                PropertyDescriptor property = beanDescriptor.getConstraintsForProperty(leafKey);
                if (property != null) {
                    Annotation[] orderedAnnotations = null;
                    for (Class<?> c = classType; c != null; c = c.getSuperclass()) { // we also check the fields of all superclasses
                        java.lang.reflect.Field field = null;
                        try {
                            field = c.getDeclaredField(leafKey);
                        } catch (NoSuchFieldException | SecurityException e) {
                            continue;
                        }
                        // getDeclaredAnnotations also looks for private fields; also it provides the annotations in a guaranteed order
                        orderedAnnotations = field.getDeclaredAnnotations();
                        break;
                    }
                    constraints = Constraints.displayableConstraint(
                            property.findConstraints().unorderedAndMatchingGroups(groups != null ? groups : new Class[]{Default.class}).getConstraintDescriptors(),
                            orderedAnnotations
                        );
                }
            }
        }

        return new Field(this, key, constraints, format, fieldErrors, fieldValue);
    }

    public String toString() {
        return "Form(of=" + backedType + ", data=" + data + ", value=" + value +", errors=" + errors + ")";
    }

    /**
     * Set the locale of the current request (if there is one) into Spring's LocaleContextHolder.
     *
     * @param code The code to execute while the locale is set
     * @return the result of the code block
     */
    private static <T> T withRequestLocale(Supplier<T> code) {
        try {
            LocaleContextHolder.setLocale(Http.Context.current().lang().toLocale());
        } catch(Exception e) {
            // Just continue (Maybe there is no context or some internal error in LocaleContextHolder). System default locale will be used.
        }
        try {
            return code.get();
        } finally {
            LocaleContextHolder.resetLocaleContext(); // Clean up ThreadLocal
        }
    }

    /**
     * A form field.
     */
    public static class Field {

        private final Form<?> form;
        private final String name;
        private final List<Tuple<String,List<Object>>> constraints;
        private final Tuple<String,List<Object>> format;
        private final List<ValidationError> errors;
        private final String value;

        /**
         * Creates a form field.
         *
         * @param name the field name
         * @param constraints the constraints associated with the field
         * @param format the format expected for this field
         * @param errors the errors associated with this field
         * @param value the field value ,if any
         */
        public Field(Form<?> form, String name, List<Tuple<String,List<Object>>> constraints, Tuple<String,List<Object>> format, List<ValidationError> errors, String value) {
            this.form = form;
            this.name = name;
            this.constraints = constraints;
            this.format = format;
            this.errors = errors;
            this.value = value;
        }

        /**
         * Returns the field name.
         *
         * @return The field name.
         */
        public String name() {
            return name;
        }

        /**
         * Returns the field value, if defined.
         *
         * @return The field value, if defined.
         */
        public String value() {
            return value;
        }

        public String valueOr(String or) {
            if (value == null) {
                return or;
            }
            return value;
        }

        /**
         * Returns all the errors associated with this field.
         *
         * @return The errors associated with this field.
         */
        public List<ValidationError> errors() {
            return errors;
        }

        /**
         * Returns all the constraints associated with this field.
         *
         * @return The constraints associated with this field.
         */
        public List<Tuple<String,List<Object>>> constraints() {
            return constraints;
        }

        /**
         * Returns the expected format for this field.
         *
         * @return The expected format for this field.
         */
        public Tuple<String,List<Object>> format() {
            return format;
        }

        /**
         * Return the indexes available for this field (for repeated fields ad List)
         */
        @SuppressWarnings("rawtypes")
        public List<Integer> indexes() {
            if(form == null) {
                return new ArrayList<>(0);
            }
            return form.value().map((Function<Object, List<Integer>>) value -> {
                BeanWrapper beanWrapper = new BeanWrapperImpl(value);
                beanWrapper.setAutoGrowNestedPaths(true);
                String objectKey = name;
                if (form.name() != null && name.startsWith(form.name() + ".")) {
                    objectKey = name.substring(form.name().length() + 1);
                }

                List<Integer> result = new ArrayList<>();
                if (beanWrapper.isReadableProperty(objectKey)) {
                    Object value1 = beanWrapper.getPropertyValue(objectKey);
                    if (value1 instanceof Collection) {
                        for (int i = 0; i<((Collection) value1).size(); i++) {
                            result.add(i);
                        }
                    }
                }

                return result;
            }).orElseGet(() -> {
                Set<Integer> result = new TreeSet<>();
                Pattern pattern = Pattern.compile("^" + Pattern.quote(name) + "\\[(\\d+)\\].*$");

                for (String key: form.data().keySet()) {
                    java.util.regex.Matcher matcher = pattern.matcher(key);
                    if (matcher.matches()) {
                        result.add(Integer.parseInt(matcher.group(1)));
                    }
                }

                List<Integer> sortedResult = new ArrayList<>(result);
                Collections.sort(sortedResult);
                return sortedResult;
            });
        }

        /**
         * Get a sub-field, with a key relative to the current field.
         */
        public Field sub(String key) {
            String subKey;
            if (key.startsWith("[")) {
                subKey = name + key;
            } else {
                subKey = name + "." + key;
            }
            return form.field(subKey);
        }

        public String toString() {
            return "Form.Field(" + name + ")";
        }

    }

}
