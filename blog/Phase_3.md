# To Kotlin, or Not To Kotlin?

**or... "The Emperor's New Code"**

## Starting Point

When I first chose to implement this plugin using Kotlin, my reasoning seemed to be sound.

To re-iterate from [Phase 1](phase_1.md):

- Kotlin has [its own section](https://www.jetbrains.org/intellij/sdk/docs/tutorials/kotlin.html) in the Platform SDK guide, this makes it a supported configuration

- IntelliJ makes IDEA, they also make Kotlin, I could reasonably expect them to be [eating their own dogfood](https://en.wikipedia.org/wiki/Eating_your_own_dog_food) here to iron out any kinks, and maybe even to provide additional functionality in the SDK dedicated to Kotlin

-  I'd be working primarily with pure-Java APIs, and Kotlin markets itself heavily on offering good Java compatibility

- Compared to scala (which would be my other option) I've often seen Kotlin described as being a simpler language. This is worth considering when writing code that I'd need other team members to read and understand when they have limited experience in any JVM language

## Questioning My Assumptions

Very quickly after starting the implementation, these reasons began looking very weak:

- The Kotlin section in the Platform SDK Guide is a single page which largely states "you can", then gives a small sales pitch for the language and shows how to configure it in a Gradle build file.

- The only extra language support seems to be a single DSL for building swing UIs, documented in the form of [a single markdown page on github](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ui/layout/readme.md)

and I was getting confusing error messages, this was a favourite:

```
Cannot choose among the following candidates without completing type inference: 
public fun <T> lazy(initializer: () -> ???): Lazy<???> defined in kotlin
public fun <T> lazy(initializer: () -> ???): Lazy<???> defined in kotlin
> Task :compileKotlin FAILED
```

Or being informed that I needed to update the version of Kotlin in my build file, because it was at version `1.2.51` and so out of sync with the Kotlin IntelliJ plugin at version `1.2.51`... I was only able to clear that particular error by reinstalling the plugin.

## The Honeymoon is OVER

I was getting inexplicable errors, and the Kotlin swing DSL seemed to be leagues behind scala-swing in terms of usefulness.  Perhaps I had chosen badly, but what about the jewel in Kotlin's crown... Java Interop?

Many of Kotlin's features were already familiar to me from scala, and I knew from this experience that they had a good story to tell about interop:

- `val`/`var`
- type inference
- extension methods
- named parameters
- lambdas
- `name: Type` syntax
- immutable collections

The use of lambdas was different, you'd declare a lambda to be the final parameter to a method, and then at use time it would be split off and be supplied as a separate block.

[[example goes here]]

I found this to be slightly "_magical_", but understood how it would work well with the **large** number of methods that had been defined in the standard Java API taking a [SAM](http://baddotrobot.com/blog/2014/04/07/functional-interfaces-in-java8/) type as the final parameter.

I didn't _like_ the lambda design, but it wasn't hard for me to adapt to.

## Property is Nine Tenths of the Law

My main pain point wasn't lambdas, it was properties.

Scala and Kotlin have both moved away from from the traditional Java-Bean convention of separately defining private fields and `setXxx` and `getXxx` methods as property accessors.  In both languages the concept of private fields has all but disappeared - instead you define a property that defines the accessors and the backing field is silently handled for you. But, beyond this, the two languages have taken **very** different approaches.

### Scala Properties

Scala adopted the [Uniform Access Principle](???) - you can define a property as:

```scala
def propA = "Hello World"
//or
val propB = 42
//or
var propC = 69
```

And in Java, the compiled bytecode would be equivalent to:

```java
String propA() { return "Hello World"; }

final int propB_ = 42;
int propB() { return propB_; }

int propC_ = 69;
int propC() { return propC_; }
void propC_=(int newval) { propC_ = newval; }
//actually, it's propC_$eq, but let's not complicate things
```

This technique has a really useful quality, which is why it's referred to as "uniform" access.  You can define a property as:

```scala
trait Super {
  def foo: Int
  //abstract, no backing field is emitted
}
```

and then override it with:

```scala
class Sub extends Super {
  override lazy val foo: Int = 42
  //A lazy val isn't evaluated until it's first accessed
}
```

Note that the convention here, by design, **does not** match Java's use of `get` and `set`, and that the compiler will always name backing fields to avoid any conflicts.

If you want to override a getter/setter from an inherited Java interface, then you can always fall back to the Java method names.

```scala
class Sub extends SomeJavaObject {
  override def getFoo: Int = ???
  override def setFoo(newVal: Int) = ???
}
```

Similarly, if you want to expose properties according to the JavaBean convention, there's a helper annotation:

```scala
class Sub {
  @BeanProperty var foo: Int = 42
  // getFoo and setFoo will be generated as well as foo and foo_=
}
```

So you can quite happily stick with the UAP convention, then explicitly opt-in to `get`/`set` methods only as and when you need them.  The interop story is a good one, it's available when you need it and is simple to do, without risk of naming conflict.

## [Kotlin Properties](#kotlin-properties)

Kotlin opted for a different approach - the language will *always* generate properties as `get`/`set` methods... whether you want it to or not.

So this:

```kotlin
fun getPropA = "Hello World"
//or
val propA = 42
//or
var propB = 69
```

is equivalent to:

```java
String getPropA() { return "Hello World"; }

final int propB_ = 42;
int getPropB() { return propB_; }

int propC_ = 69;
int getPropC() { return _prop2; }
void setPropC(int newval) { _prop2 = newval; }
```

In reverse, you can also call:

```kotlin
obj.foo = 42
println(obj.foo)
```

and the language will rewrite it to use `obj.getFoo()` and `obj.setFoo()` as necessary.  It will do so even if there's a `public int foo` field also available on `obj`.  You're permanently opted in to the feature, and no opt-out is possible.

Overriding also has issues.  So this, for example, is **not** valid:

```kotlin
class Sub : SomeJavaObject {
  override var foo: Int = 42
}
```

Nor is the dedicated kotlin getter/setter syntax:

```kotlin
class Sub : SomeJavaObject {
  var foo: Int
    override get() = ??? //can't do this
    set(x: Int) { ??? }
}
```

There's no uniform access here.  A property will _generate_ accessors, but it won't _override_ them.  If you want to override, you must also fall back to the Java convention:

```kotlin
class Sub : SomeJavaObject {
  private var foo = 42;
  override fun getFoo(): Int { return foo; }
  override fun setFoo(newval: Int) { foo = newval; }
}
```

... which won't compile, because the generated accessors for `var foo` will be in conflict.  You have to choose a unique name here and get no compiler support to do so.  The convention is prepend/append an underscore to the name and then pray.

The _idea_ is an appealing one.  Make getters and setters _look like_ they're just a property.  It's a long established technique used in expression languages for spring, struts, templating engines, etc, etc.  But as a way to **define** properties the Kotlin implementation seems fraught with potential name clashes and poor integration with other platform features (like overloading).

## The Icing on The Cake: JSON Serialisation

A good litmus test of any language and the strength of its surrounding ecosystem is the quality of libraries available for both parsing JSON and serialising to the format.

JSON has rapidly become the lingua franca format for moving structured data between systems and languages in an internet-connected world.  This means that using JSON is one of the first things you'll need to do when building an application (or plugin) that has any sort of connectivity... and you'll want it to be as easy as possible.

I found a number of JSON libraries for Kotlin, perhaps the most promising of which was [Klaxon](https://github.com/cbeust/klaxon) - written by Cederic Beust, author of TestNG and a long-time aquaintance with whom I've had many heated discussions...

It was good, but missed a number of features I was looking for:  An intermediate representation that I could output in different styles, type safety, operators for mapping over JSON trees, efficient handling of algebraic data types, compile-time checking of literal/interoplated JSON strings, etc.

In all fairness to Cedric (and to authors of other JSON libs), many of these limitations are imposed by the language.  You can't do things that require Type Classes, interpolated string contexts, and macros if you don't have Type Classes, interpolated string contexts, and macros!  _Some_ of the libraries offered _some_ of the features I wanted (with varying levels of maturity) by using Kotlin reflection, but I found them all to be very limiting and I've never been comfortable with sacrificing compile-time safety to reflection if it can possibly be avoided

This was the final nail in Kotlin's coffin.  By switching to Scala I'd be able to use [Circe](https://circe.github.io/circe/) (by far the best JSON library I've ever used, including JavaScript itself) and the `circe-literal` module to build JSON structures in a modular fashion with a clean intuitive syntax.

##In Conclusion

The project was still small enough at this stage, so I made the switch, and haven't regretted doing so for a second.






