/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.http;

import java.lang.reflect.Method;

import play.mvc.Action;
import play.mvc.Http.Request;

/**
 * An interface for creating Java actions from Java methods.
 */
@FunctionalInterface
public interface ActionCreator {
    /**
     * Call to create the root Action for a Java controller method call.
     *
     * The request and actionMethod values are passed for information.  Implementations of this method should create
     * an instance of Action that invokes the injected action delegate.
     *
     * @param request The HTTP Request
     * @param actionMethod The action method containing the user code for this Action.
     * @return The default implementation returns a raw Action calling the method.
     */
    Action createAction(Request request, Method actionMethod);

    /**
     * Call to wrap the outer action of a Java application.
     *
     * This method is passed a fully composed action, allowing a last final global interceptor to be added to the
     * action if required.
     *
     * @deprecated Use createAction and set play.http.actionComposition.executeActionCreatorActionFirst = true
     *
     * @param action The action to wrap.
     * @return A wrapped action.
     */
    @Deprecated
    default Action wrapAction(Action action) {
        return action;
    }
}
