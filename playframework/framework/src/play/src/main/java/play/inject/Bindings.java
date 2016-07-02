/*
 * Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com>
 */
package play.inject;

import play.api.inject.BindingKey;

public class Bindings {

    /**
     * Create a binding key for the given class.
     */
    public static final <T> BindingKey<T> bind(Class<T> clazz) {
        return new BindingKey(clazz);
    }

}
