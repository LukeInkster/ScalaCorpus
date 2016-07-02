<!--- Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com> -->
# Handling form submission

Before you start with Play forms, read the documentation on the [[Play enhancer|PlayEnhancer]]. The Play enhancer generates accessors for fields in Java classes for you, so that you don't have to generate them yourself. You may decide to use this as a convenience. All the examples below show manually writing accessors for your classes.

## Defining a form

The `play.data` package contains several helpers to handle HTTP form data submission and validation. The easiest way to handle a form submission is to define a `play.data.Form` that wraps an existing class:

@[user](code/javaguide/forms/u1/User.java)

To wrap a class you have to inject a `play.data.FormFactory` into your Controller which then allows you to create the form:

@[create](code/javaguide/forms/JavaForms.java)

> **Note:** The underlying binding is done using [Spring data binder](https://docs.spring.io/spring/docs/4.2.4.RELEASE/spring-framework-reference/html/validation.html).

This form can generate a `User` result value from `HashMap<String,String>` data:

@[bind](code/javaguide/forms/JavaForms.java)

If you have a request available in the scope, you can bind directly from the request content:

@[bind-from-request](code/javaguide/forms/JavaForms.java)

## Defining constraints

You can define additional constraints that will be checked during the binding phase using JSR-303 (Bean Validation) annotations:

@[user](code/javaguide/forms/u2/User.java)

> **Tip:** The `play.data.validation.Constraints` class contains several built-in validation annotations.

You can also define an ad-hoc validation by adding a `validate` method to your top object:

@[user](code/javaguide/forms/u3/User.java)

The message returned in the above example will become a global error.

The `validate`-method can return the following types: `String`, `List<ValidationError>` or `Map<String,List<ValidationError>>`

`validate` method is called after checking annotation-based constraints and only if they pass.  If validation passes you must return `null` . Returning any non-`null` value (empty string or empty list) is treated as failed validation.

`List<ValidationError>` may be useful when you have additional validations for fields. For example:

@[list-validate](code/javaguide/forms/JavaForms.java)

Using `Map<String,List<ValidationError>>` is similar to `List<ValidationError>` where map's keys are error codes similar to `email` in the example above.

## Handling binding failure

Of course if you can define constraints, then you need to be able to handle the binding errors.

@[handle-errors](code/javaguide/forms/JavaForms.java)

Typically, as shown above, the form simply gets passed to a template.  Global errors can be rendered in the following way:

@[global-errors](code/javaguide/forms/view.scala.html)

Errors for a particular field can be rendered in the following manner:

@[field-errors](code/javaguide/forms/view.scala.html)


## Filling a form with initial default values

Sometimes you’ll want to fill a form with existing values, typically for editing:

@[fill](code/javaguide/forms/JavaForms.java)

> **Tip:** `Form` objects are immutable - calls to methods like `bind()` and `fill()` will return a new object filled with the new data.

## Handling a form that is not related to a Model

You can use a `DynamicForm` if you need to retrieve data from an html form that is not related to a `Model`:

@[dynamic](code/javaguide/forms/JavaForms.java)

## Register a custom DataBinder

In case you want to define a mapping from a custom object to a form field string and vice versa you need to register a new Formatter for this object.
You can achieve this by registering a provider for `Formatters` which will do the proper initialization.
For an object like JodaTime's `LocalTime` it could look like this:

@[register-formatter](code/javaguide/forms/FormattersProvider.java)

After defining the provider you have to bind it:

@[register-formatter](code/javaguide/forms/FormattersModule.java)

Finally you have to disable Play's default `FormattersModule` and instead enable your module in `application.conf`:

    play.modules.enabled += "com.example.FormattersModule"
    play.modules.disabled += "play.data.format.FormattersModule"

When the binding fails an array of errors keys is created, the first one defined in the messages file will be used. This array will generally contain:

    ["error.invalid.<fieldName>", "error.invalid.<type>", "error.invalid"]

The errors keys are created by [Spring DefaultMessageCodesResolver](https://docs.spring.io/spring/docs/4.2.4.RELEASE/javadoc-api/org/springframework/validation/DefaultMessageCodesResolver.html), the root "typeMismatch" is replaced by "error.invalid".
