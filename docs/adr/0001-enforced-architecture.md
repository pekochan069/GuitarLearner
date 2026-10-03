# Enforce presentation and platform boundaries

GuitarLearner separates domain capabilities, presentation contracts, Circuit presenters, Material 3 Expressive rendering, platform adapters, and application assembly into Gradle modules so rendering code cannot import services. Arrow represents expected failures, structured coroutines own asynchronous work, and Metro validates dependency assembly; build checks enforce the remaining boundaries because Kotlin does not track all effects. This costs more module configuration than a single application module, but makes architectural violations fail verification instead of relying on agent instructions.

The user approved this architecture and authorized implementation in the conversation recorded by [issue #3](https://github.com/pekochan069/GuitarLearner/issues/3).
