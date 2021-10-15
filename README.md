[![dapper-compiler](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler/badge.svg?color=grey&subject=dapper-compiler)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper-compiler)
[![dapper](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper/badge.svg?subject=dapper)](https://maven-badges.herokuapp.com/maven-central/io.github.jbock-java/dapper)

This is a fork of [dagger2](https://github.com/google/dagger) but with a `module-info.java`,
a standard gradle project layout, and without kotlin support.
It was forked from dagger version `2.37`, which is the last dagger
version that did not depend on the "xprocessing" kotlin library.

In order to be modular, dapper uses `jakarta.inject` annotations, instead of `javax.inject`.
It also requires Java Version 11 or higher.

Add to `module-info.java`:

````java
requires jakarta.inject;
requires dagger;
````

Gradle users add this to `build.gradle`:

````groovy
implementation('io.github.jbock-java:dapper:1.0')
annotationProcessor('io.github.jbock-java:dapper-compiler:1.0')
````

For maven users, there is the [modular-thermosiphon](https://github.com/jbock-java/modular-thermosiphon) sample project.

Some of the integration tests from the [javatests](https://github.com/jbock-java/dapper-javatests) directory could not
be run as part of the regular build. Ther compilation requires the result of the actual annotation processing.
I could only get it to work with a released artifact, not with a gradle inter-module dependency.
This is why [dagger-javatests](https://github.com/jbock-java/dapper-javatests) was created.
A new release should be tested there first, via staging repo.
