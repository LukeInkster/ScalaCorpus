
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcFII$sp extends JFunction2 {
    abstract float apply$mcFII$sp(int v1, int v2);

    default Object apply(Object v1, Object v2) { return (Float) apply$mcFII$sp((Integer) v1, (Integer) v2); }
}
