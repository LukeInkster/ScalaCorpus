
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcJII$sp extends JFunction2 {
    abstract long apply$mcJII$sp(int v1, int v2);

    default Object apply(Object v1, Object v2) { return (Long) apply$mcJII$sp((Integer) v1, (Integer) v2); }
}
