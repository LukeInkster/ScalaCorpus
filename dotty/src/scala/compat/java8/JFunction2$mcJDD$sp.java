
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcJDD$sp extends JFunction2 {
    abstract long apply$mcJDD$sp(double v1, double v2);

    default Object apply(Object v1, Object v2) { return (Long) apply$mcJDD$sp((Double) v1, (Double) v2); }
}
