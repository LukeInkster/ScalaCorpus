
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcIDJ$sp extends JFunction2 {
    abstract int apply$mcIDJ$sp(double v1, long v2);

    default Object apply(Object v1, Object v2) { return (Integer) apply$mcIDJ$sp((Double) v1, (Long) v2); }
}
