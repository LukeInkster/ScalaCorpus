<!--- Copyright (C) 2009-2016 Lightbend Inc. <https://www.lightbend.com> -->
# The Play cache API

Caching data is a typical optimization in modern applications, and so Play provides a global cache.

> An important point about the cache is that it behaves just like a cache should: the data you just stored may just go missing.

For any data stored in the cache, a regeneration strategy needs to be put in place in case the data goes missing. This philosophy is one of the fundamentals behind Play, and is different from Java EE, where the session is expected to retain values throughout its lifetime.

The default implementation of the Cache API uses [EHCache](http://ehcache.org/).

## Importing the Cache API

Add `cache` into your dependencies list. For example, in `build.sbt`:

```scala
libraryDependencies ++= Seq(
  cache,
  ...
)
```

## Accessing the Cache API

The cache API is defined by the [AsyncCacheApi](api/scala/play/api/cache/AsyncCacheApi.html) and [SyncCacheApi](api/scala/play/api/cache/SyncCacheApi.html) traits, depending on whether you want an asynchronous or synchronous implementation, and can be injected into your component like any other dependency.  For example:

@[inject](code/ScalaCache.scala)

> **Note:** The API is intentionally minimal to allow several implementation to be plugged in. If you need a more specific API, use the one provided by your Cache plugin.

Using this simple API you can store data in cache:

@[set-value](code/ScalaCache.scala)

And then retrieve it later:

@[get-value](code/ScalaCache.scala)

There is also a convenient helper to retrieve from cache or set the value in cache if it was missing:

@[retrieve-missing](code/ScalaCache.scala)

You can specify an expiry duration by passing a duration, by default the duration is infinite:

@[set-value-expiration](code/ScalaCache.scala)

To remove an item from the cache use the `remove` method:

@[remove-value](code/ScalaCache.scala)

Note that the [SyncCacheApi](api/scala/play/api/cache/SyncCacheApi.html) has the same API, except it returns the values directly instead of using futures.

## Accessing different caches

It is possible to access different caches.  The default cache is called `play`, and can be configured by creating a file called `ehcache.xml`.  Additional caches may be configured with different configurations, or even implementations.

If you want to access multiple different ehcache caches, then you'll need to tell Play to bind them in `application.conf`, like so:

    play.cache.bindCaches = ["db-cache", "user-cache", "session-cache"]

By default, Play will try to create these caches for you. If you would like to define them yourself in `ehcache.xml`, you can set:

    play.cache.createBoundCaches = false

Now to access these different caches, when you inject them, use the [NamedCache](api/java/play/cache/NamedCache.html) qualifier on your dependency, for example:

@[qualified](code/ScalaCache.scala)

## Caching HTTP responses

You can easily create smart cached actions using standard Action composition.

> **Note:** Play HTTP `Result` instances are safe to cache and reuse later.

The [Cached](api/scala/play/api/cache/Cached.html) class helps you build cached actions.

@[cached-action-app](code/ScalaCache.scala)

You can cache the result of an action using a fixed key like `"homePage"`.

@[cached-action](code/ScalaCache.scala)

If results vary, you can cache each result using a different key. In this example, each user has a different cached result.

@[composition-cached-action](code/ScalaCache.scala)

### Control caching

You can easily control what you want to cache or what you want to exclude from the cache.

You may want to only cache 200 Ok results.

@[cached-action-control](code/ScalaCache.scala)

Or cache 404 Not Found only for a couple of minutes

@[cached-action-control-404](code/ScalaCache.scala)

## Custom implementations

It is possible to provide a custom implementation of the cache API that either replaces, or sits along side the default implementation.

To replace the default implementation, you'll need to disable the default implementation by setting the following in `application.conf`:

```
play.modules.disabled += "play.api.cache.EhCacheModule"
```

You can then implement [AsyncCacheApi](api/java/play/cache/AsyncCacheApi.html) and bind it in the DI container. You can also bind [SyncCacheApi](api/java/play/cache/SyncCacheApi.html) to [DefaultSyncCacheApi](api/java/play/cache/DefaultSyncCacheApi.html), which simply wraps the async implementation.

To provide an implementation of the cache API in addition to the default implementation, you can either create a custom qualifier, or reuse the `NamedCache` qualifier to bind the implementation.
