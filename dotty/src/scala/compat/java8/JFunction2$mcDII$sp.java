
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcDII$sp extends JFunction2 {
    abstract double apply$mcDII$sp(int v1, int v2);

    default Object apply(Object v1, Object v2) { return (Double) apply$mcDII$sp((Integer) v1, (Integer) v2); }
}
