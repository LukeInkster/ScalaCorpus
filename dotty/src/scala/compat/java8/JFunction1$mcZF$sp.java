
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcZF$sp extends JFunction1 {
    abstract boolean apply$mcZF$sp(float v1);

    default Object apply(Object t) { return (Boolean) apply$mcZF$sp((Float) t); }
}
