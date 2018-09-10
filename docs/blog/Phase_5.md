# Grasping the Native Nettle

**or... "How I Stopped Worrying and Learned to Love The `AnAction`"**

## Somebody Needs to Say This

IntelliJ - as a product - makes for a fantastic and remarkably powerful tool.  99% of functionality you need for day-to-day development (except Snyk... until now) is all there in one place and generally works well.  It's easily the best Java IDE out there, and can rightly be called a developer's best friend.

In direct contradiction to this, IntelliJ - as a platform/framework - is hostile to developers.

The framework itself seems fairly reasonable and, once you've got into it, has plenty of useful functionality which seems well designed.  As a Scala dev I find the heavy use of reflection, stringly-typed lookups, and global singletons to be somewhat old fashioned - it's certainly not how I'd design a system myself nowadays.  But I'm certainly not going to call out IntelliJ for **that**.  One must remember how old the codebase is, and certain realities are imposed upon it by the design of Swing, by the single UI thread, and by the need to make it extensible.

I'd defy anyone to point out a product with the same longevity, and built on the same Swing framework, with the same capabilities, that is equally well designed.

It's not the product itself that fails, it's the surrounding ecosystem, the reference material, and other resources available to a want-to-be plugin author.  This thing is so opaque that it could give [Vantablack™](https://www.surreynanosystems.com/vantablack) a run for its money.  The online documentation is sparse, misleading, lacks (or fails to draw proper attention to) vital features, and in places is outright wrong.

When a significant proportion of your SDK guide is mostly just links to source code, it needs improvement.  When that source code contains absolutely **no** JavaDoc, you start to have a problem on your hands.  When - in one noteable case - a linked source file turns out to have been deleted *FIVE YEARS AGO*... **and** there are big sections of functionality not documented at all, then it's become a major problem.

The other issue I encountered is that the documentation (what exists of it) is *very*... **very...** heavily biased towards the working of structured editors.  I can only assume that Jetbrains thought most plugins would be to implement support for a new language, and so documented for that particular use-case.  If I had been writing this sort of plugin myself, then I suspect my experience would have been far less fraught.

I'm frankly amazed that IntelliJ has as many plugins as it does, given how hard a nut this is to crack.
