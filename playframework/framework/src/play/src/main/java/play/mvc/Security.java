/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.mvc;

import play.inject.Injector;
import play.mvc.Http.*;

import java.lang.annotation.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import javax.inject.Inject;

/**
 * Defines several security helpers.
 */
public class Security {

    /**
     * Wraps the annotated action in an <code>AuthenticatedAction</code>.
     */
    @With(AuthenticatedAction.class)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Authenticated {
        Class<? extends Authenticator> value() default Authenticator.class;
    }

    /**
     * Wraps another action, allowing only authenticated HTTP requests.
     * <p>
     * The user name is retrieved from the session cookie, and added to the HTTP request's
     * <code>username</code> attribute.
     */
    public static class AuthenticatedAction extends Action<Authenticated> {

        private final Injector injector;

        @Inject
        public AuthenticatedAction(Injector injector) {
            this.injector = injector;
        }

        public CompletionStage<Result> call(final Context ctx) {
            Authenticator authenticator = injector.instanceOf(configuration.value());
            String username = authenticator.getUsername(ctx);
            if(username == null) {
                Result unauthorized = authenticator.onUnauthorized(ctx);
                return CompletableFuture.completedFuture(unauthorized);
            } else {
                try {
                    ctx.request().setUsername(username);
                    return delegate.call(ctx).whenComplete(
                        (result, error) -> ctx.request().setUsername(null)
                    );
                } catch (Exception e) {
                    ctx.request().setUsername(null);
                    throw e;
                }
            }
        }

    }

    /**
     * Handles authentication.
     */
    public static class Authenticator extends Results {

        /**
         * Retrieves the username from the HTTP context; the default is to read from the session cookie.
         *
         * @param ctx the current request context
         * @return null if the user is not authenticated.
         */
        public String getUsername(Context ctx) {
            return ctx.session().get("username");
        }

        /**
         * Generates an alternative result if the user is not authenticated; the default a simple '401 Not Authorized' page.
         *
         * @param ctx the current request context
         * @return a <code>401 Not Authorized</code> result
         */
        public Result onUnauthorized(Context ctx) {
            return unauthorized(views.html.defaultpages.unauthorized.render());
        }

    }


}
