
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcFJ$sp extends JFunction1 {
    abstract float apply$mcFJ$sp(long v1);

    default Object apply(Object t) { return (Float) apply$mcFJ$sp((Long) t); }
}
