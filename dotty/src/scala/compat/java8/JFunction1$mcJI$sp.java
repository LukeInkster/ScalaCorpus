
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction1$mcJI$sp extends JFunction1 {
    abstract long apply$mcJI$sp(int v1);

    default Object apply(Object t) { return (Long) apply$mcJI$sp((Integer) t); }
}
