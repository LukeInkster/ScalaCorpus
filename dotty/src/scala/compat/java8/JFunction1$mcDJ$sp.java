
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcDJ$sp extends JFunction1 {
    abstract double apply$mcDJ$sp(long v1);

    default Object apply(Object t) { return (Double) apply$mcDJ$sp((Long) t); }
}
