
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcIIJ$sp extends JFunction2 {
    abstract int apply$mcIIJ$sp(int v1, long v2);

    default Object apply(Object v1, Object v2) { return (Integer) apply$mcIIJ$sp((Integer) v1, (Long) v2); }
}
