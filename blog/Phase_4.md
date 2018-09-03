# Webserving in the Small

**or... "Do you want MIME with that?"**


## Building a Teeny-Tiny Webserver

As [previously stated](Phase_2.md), the bulk of this plugin is in the form of an embedded
web page and I'm using [nanohttpd](http://nanohttpd.org/) as a way
to serve it content (due to the many, many problems I encountered trying to
find an elegant way to handle internal links)

NannoHTTP lives up to its name, the thing is barely a rounding error in the final packaged size of the plugin, but with great (lack of) size comes great (lack of) built-in functionality.  I had to build **everything** else myself.  parameter parsing, MIME-type lookup (it's just a Map), routing, the works.

There's some helper functionality I added for things like ensuring we have scan results available before displaying them, ensuring that the user is logged in, and rendering Circe JSON as a result, but all-in it's still under 500 lines of code split across 5 classes - not counting the handlebars template engine (more on that later...).

It used to be much smaller too, but I also chose to adopt a more conventional "router" design that would be more familiar to collegues who were familiar with the model from other web servers.  That decision almost doubled the size of the code, but definitely made it more self-documenting.

## Going Fast and Fixing Things

The standard convention for _any_ Java-based web server, when not rendering content from the file system, is to tuck it away in the classpath under `/WEB-INF`, I followed suit here.

...With one noteable exception.  I added a mechanism to detect if we're running in an expanded form, and not our of a JAR file.  Using this as an indicator that the application was running inside a test instance of IntelliJ, I had it use the `/WEB-INF` directory straight from the source code and not the copy that the gradle build had "packaged".

Why?  Because this allowed me to change my handlebars templates (later...) directly in the source, and see those changes with a simple page refresh.  No need to go though a full recompile cycle first!  It was especially helpful when testing content directly in Chrome - another significant benefit of implementing a web server instead of injecting content.  The inspector proved to be invaluable.

## Thyme For Handlebars!

One essential component of the grand design was being able to inject data from the application into the web view.  This meant generating HTML, and there was absolutely **no way** I was going to be builing that by hand; not even with the benefits of multi-line string interpolation.  On top of having to ensure that tags are correctly paired off, it's just way too fragile as regards things like escaping.

So templating was the way forward.  In addition to allowing me to see changes without having to restart the entire app, and giving me the full force of IntelliJ's syntax highlighting and, it would also catch well-formdness bugs that might otherwise have left me scratching my head over obscure bugs.

In the bad old Kotlin days, I began by using [Thymeleaf](https://www.thymeleaf.org/).  This is a mature and reasonably capable Java template engine, and did everything I needed (including recursive templates).  It's a little on the slow side, but at the heady volume of just one client that wasn't a concern.

It didn't take long for me to question this decision though, in large part because Thymeleaf introduces a security vulnerability via its use of OGNL - and we eat our own dogfood at Snyk).

Moving to Scala gave me the impetus I needed to make the change.  That same insecure OGNL parser that Tymeleaf uses internally assumes javabean conventions.  Scala, for [good reasons](Phase_3#kotlin-properties), uses a different convention.

This needn't be a deal-breaker, as Scala can certainly emit Java accessors without too much pain.  But OGNL **also** assumes that all collections will be Java collections (complete with compulsory mutator methods) - which Scala's aren't.  I couldn't see any sane way to retrofit that support in OGNL, nor was I willing to implement a significant part of the application in terms of Java collections, especially given my existing security concerns!

A replacement was needed.

First to be quickly ruled out was [twirl](https://www.playframework.com/documentation/2.6.x/ScalaTemplates), the engine behind the play framework.  This is a very effective engine with one major flaw (for my particular needs)... It works by generating scala code from the template, which it then compiles.  This makes for a blazing fast runtime engine, but also sacrifices the hot-reloading property that I needed.

Second, I looked at [scalate](https://github.com/scalate/scalate).  Another engine that I'm very familiar with, and originally written by another aquaintace of mine - James Strachan, author of the Groovy language.  Scalate had a lot in its favour, including support for mustache, which is a subset of the handlebars engine that we already use extensively at Snyk.

Unfortunately, mustache is something of a second-class citizen amongst the languages supported by Scalate.  From the documentation, I couldn't see a clear way to access some of the features I'd need if I were going to be able to re-use some of our existing assets.

Further afield then... If I want handlebars (my reasoning went), then why should I settle for a subset instead of the full thing?  This led me to [Handlebars.java](http://jknack.github.io/handlebars.java/).

Handlebars is a Java (not Scala) engine - the clue's in the name!  This meant it had the same problem as OGNL, it assumed that all collection types would be Java collection types.  Unlike Thymeleaf and OGNL, however, it had a clean modern design; with the mechanism for extending it with this support being reasonably well documented, and fairly painless to implement.

So I did just that... I extended Handlebars.java with support for Scala collections, and with scala-native property accessors so that I wouldn't need to add annotations throughout the codebase to emit Java accessor methods.

This means that the Snyk IntelliJ plugin contains what is quite possibly the world's first implementation of Handlebars that can speak native Scala - though I fully intend to commit my extensions back to the underlying project so that others might benefit from it too.



