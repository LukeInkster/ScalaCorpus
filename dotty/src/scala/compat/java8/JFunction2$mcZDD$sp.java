
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcZDD$sp extends JFunction2 {
    abstract boolean apply$mcZDD$sp(double v1, double v2);

    default Object apply(Object v1, Object v2) { return (Boolean) apply$mcZDD$sp((Double) v1, (Double) v2); }
}
