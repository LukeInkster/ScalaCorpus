
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcIF$sp extends JFunction1 {
    abstract int apply$mcIF$sp(float v1);

    default Object apply(Object t) { return (Integer) apply$mcIF$sp((Float) t); }
}
