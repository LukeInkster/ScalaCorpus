
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcDID$sp extends JFunction2 {
    abstract double apply$mcDID$sp(int v1, double v2);

    default Object apply(Object v1, Object v2) { return (Double) apply$mcDID$sp((Integer) v1, (Double) v2); }
}
