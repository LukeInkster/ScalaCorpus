
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcJIJ$sp extends JFunction2 {
    abstract long apply$mcJIJ$sp(int v1, long v2);

    default Object apply(Object v1, Object v2) { return (Long) apply$mcJIJ$sp((Integer) v1, (Long) v2); }
}
