
/*
 * Copyright (C) 2012-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package scala.compat.java8;

@FunctionalInterface
public interface JFunction2$mcZJJ$sp extends JFunction2 {
    abstract boolean apply$mcZJJ$sp(long v1, long v2);

    default Object apply(Object v1, Object v2) { return (Boolean) apply$mcZJJ$sp((Long) v1, (Long) v2); }
}
