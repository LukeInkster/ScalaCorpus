
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcDJI$sp extends JFunction2 {
    abstract double apply$mcDJI$sp(long v1, int v2);

    default Object apply(Object v1, Object v2) { return (Double) apply$mcDJI$sp((Long) v1, (Integer) v2); }
}
