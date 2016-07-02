---
title: Version Upgrade
---

# Version Upgrade

The 0.8.x series has been rewritten from the ground up to facilitate easy
building of various types of machine learning engines. The changes are
fundamental and requires a migration to properly upgrade from 0.7.x to 0.8.x.

## Conceptual Changes

Before upgrading, it is necessary to understand some fundamental changes and
limitation between the 0.7.x and the 0.8.x series.

### Event-based Data

In 0.7.x, users and items are stored separately from user-to-item actions. In
0.8.x, users, items, and user-to-item actions are all recorded as events.

In 0.8.x, creating, updating, and deleting users and items are recorded as
events. When an engine is trained, information about users and items are built
from an aggregation of these events to form the most recent view of users and
items. The most recent event about a user or an item will always take
precedence.

The concept of user-to-item action maps directly to the event-based data model
in 0.8.x and requires almost no change during migration.

### Web UI Users

0.8.x assumes a trusted environment and no longer associates apps with a
particular web-based user. This data does not need to be migrated.

### Apps

Apps and its access key can now be created using the command line interface,
which can be conveniently scripted.

### Engines and Algorithms

Engines and algorithms settings in 0.7.x are now stored in individual engine
variant JSON files which allow easy version control and programmatic access.
