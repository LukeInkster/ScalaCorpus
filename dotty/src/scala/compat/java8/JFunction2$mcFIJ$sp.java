
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcFIJ$sp extends JFunction2 {
    abstract float apply$mcFIJ$sp(int v1, long v2);

    default Object apply(Object v1, Object v2) { return (Float) apply$mcFIJ$sp((Integer) v1, (Long) v2); }
}
