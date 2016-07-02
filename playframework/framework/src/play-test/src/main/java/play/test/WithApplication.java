/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.test;

import akka.stream.Materializer;
import org.junit.After;
import org.junit.Before;
import play.Application;

/**
 * Provides an application for JUnit tests. Make your test class extend this class and an application will be started before each test is invoked.
 * You can setup the application to use by overriding the provideApplication method.
 * Within a test, the running application is available through the app field.
 */
public class WithApplication {

    protected Application app;

    /**
     * The application's Akka streams Materializer.
     */
    protected Materializer mat;

    /**
     * Override this method to setup the application to use.
     *
     * By default this will call the old {@link #provideFakeApplication() provideFakeApplication} method.
     *
     * @return The application to use
     */
    protected Application provideApplication() {
        return provideFakeApplication();
    }

    /**
     *
     * Override this method to setup the fake application to use.
     *
     * @deprecated use the new {@link #provideApplication() provideApplication} method instead.
     *
     * @return The fake application to use
     */
    @Deprecated
    protected Application provideFakeApplication() {
        return Helpers.fakeApplication();
    }

    @Before
    public void startPlay() {
        app = provideApplication();
        Helpers.start(app);
        mat = app.getWrappedApplication().materializer();
    }

    @After
    public void stopPlay() {
        if (app != null) {
            Helpers.stop(app);
            app = null;
        }
    }

}
