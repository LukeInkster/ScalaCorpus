
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcJJI$sp extends JFunction2 {
    abstract long apply$mcJJI$sp(long v1, int v2);

    default Object apply(Object v1, Object v2) { return (Long) apply$mcJJI$sp((Long) v1, (Integer) v2); }
}
