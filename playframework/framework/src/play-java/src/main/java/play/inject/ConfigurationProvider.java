/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.inject;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import play.Configuration;

@Singleton
public class ConfigurationProvider implements Provider<Configuration> {

    private final play.api.Configuration delegate;

    @Inject
    public ConfigurationProvider(play.api.Configuration delegate) {
        this.delegate = delegate;
    }

    public Configuration get() {
        return new Configuration(delegate);
    }

}
