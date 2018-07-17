# Planning a Plugin

## In The Beginning

Tasked with creating an IDE plugin for Snyk, I chose to start my journey
with IntelliJ IDEA and to use Kotlin.

IntelliJ wasn't even really a choice, it's more popular than Eclipse and obviously
means that we (Snyk) can get our awesome new tool into the hands of as many developers as possible
as soon as possible.


## When Everything is New

Kotlin **was** a choice however - and it would be a new language for me!

Fortunately, I have a strong polyglot heritage.  This noteably includes a decade's worth of Scala experience;
and many of Kotlin's features I'd read of had Scala equivalents.  So programming on the JVM with
`val`/`var`, type inference, extension methods, named parameters, lambdas, `name: Type` syntax,
immutable collections, etc., etc. was something I felt completely comfortable with.

I would be using a language created by IntelliJ when implementing a plugin
for a tool *also* created by IntelliJ, there was a
[Kotlin-specific section on the Platform SDK Guide](https://www.jetbrains.org/intellij/sdk/docs/tutorials/kotlin.html),
and I could reasonably expect that the company would have invested in the Kotlin + Plugin Dev
experience with documentation, extension methods for common tasks, etc. etc.

When I later started the implementation, I discovered how rare that section in the documentation truly was.
If you wish to create a plugin that looks and behaves like built-in IntelliJ functionality,
there's a small mountain of vital information for plugin development that **isn't** in the guide...
This made me even more confident of my decision.

As for all the things I found to be missing?  That's my inspiration for this blog series and it
forms the backbone of these articles.

## When Everything *Looks* New

I also had no prior experience of writing an IntelliJ plugin in any other language. 
But once again this wasn't completely new territory to me;
I knew the UI framework was largely built around swing, and this is a place where I
*did* have past experience.
In fact... one of my earliest commits to the scala standard library was to scala-swing.

Just as I'd found for Kotlin, there's also a whole section in the SDK Documentation
[devoted to User Interface](https://www.jetbrains.org/intellij/sdk/docs/user_interface_components/user_interface_components.html)
.

So two learning curves to deal with, but no reason to believe that either of them would be especially steep.

## Scouting Further Afield

And so, eager with anticipation, feeling righteously armed with the blessing of the official documentation,
I drew up a list of other considerations:

- We'll want to do Eclipse too at some point, so try to avoid tight coupling to IntelliJ specifics.
  Preferably, decouple the core logic into a completely separate module from the UI logic
  _(bonus: that core logic may later be useful in other places completely unrelated to IDEs)_
   
- Conceptually, this project would do the same job as a graphical version of our existing command-line tool
  (CLI).  Which means I can use APIs provided for the CLI, and (where reasonable) adopt the same workflow
   
- Later, we'll want to be smarter, and go beyond what the CLI can do.  There were no obvious blockers
  to this in the choices made so far
   
- For now, we're sticking only to JVM languages, but want to work with **all** JVM-centric build/dependency
  management systems _already handled by Snyk_: Maven, Gradle, and SBT.  Fortunately, all three have
  IntelliJ plugins with extension points that I could use - though I'd be starting with just Maven
  
- For other build systems that we don't directly support (such as native IDEA projects, or other new build systems),
  I can still obtain a flat list of dependencies via the IDE's API to offer limited functionality 
   
- We have existing assets and resources already produced for the website.  Any of these that I could
  reuse would avoid me having to re-invent the wheel
   
- Java provides an embedded webkit instance via the JavaFX `WebView` component, this could prove to
  be **very** useful for re-using existing web-centric assets
   
- Similarly, our API provides rich vulnerability descriptions formatted in markdown; which again lends
  itself to being rendered via HTML

## Heading Forth

I set out my battle-plan!

- Write a plugin
- Write it in Kotlin
- Use the API from the maven IntelliJ plugin to determine dependency trees
- Use our existing back-end CLI API to perform the scan
- Implement the initial UI via HTML and a `WebView`
