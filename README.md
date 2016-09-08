# Manage Topics

This is a utility for maintaining kafka topic state from a .ini file.

## The Basic Idea

Declare a list of desired topics and their configuration in a file.
Then use this tool to sync state of your kafka cluster with that file.

The .ini file format looks like so:

    [DEFAULT]
    # keys from the special DEFAULT section are used
    # for every topic unless overridden
    replication = 3
    partitions = 10

    [my.namespace.turtle_created]
    replication = 1
    partitions = 99
    retention.ms = 1000000000
    # unknown keys are ignored, viz.:
    any_old_whatever = ignored

    [my.namespace.turtle_destroyed]
    # just use the defaults

The known keys are:

- partitions: number of partitions for the topic
- replication: the replication factor for the topic
- all topic configuration properties as listed here:
  http://kafka.apache.org/documentation.html#topic-config

To sync all the topics in your cluster:

    manage_topics create
    manage_topics check

For safety, excess topics will not be automatically deleted; if `check`
reveals some, you should manually delete at your leisure.

Similarly, configuration changes are not performed by this tool, only
checked. If there is a mismatch, `check` will alert you, and you should
take steps to resolve the problem.

## Other utilities

This tool has `list` and `delete` commands, because they are handy.
`list` does what it says. `delete` will delete *all* topics from your
cluster -- it'll prompt you for confirmation first. This is useful in
a development environment, probably don't use it in production.

## Roadmap

- Automatically sync configuration changes
- Expand `delete` to facilitate manual deletion of one or several topics
- Tests to cover stuff that is currently broken
  (section in ini with no properties; partition/replication override from
   command line options; probably other things)

## Developing

You'll need to [install leiningen](http://leiningen.org/#install) to build
the project.

In case it's not obvious, you'll also need Java 1.6 or better.

## Building

`lein bin` -> produce a standalone executable at target/manage-topics.

## Running

You don't need Clojure or Leiningen to run a build executable. Just Java.

Try:

    path/to/manage_topics --help
