
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcII$sp extends JFunction1 {
    abstract int apply$mcII$sp(int v1);

    default Object apply(Object t) { return (Integer) apply$mcII$sp((Integer) t); }
}
