# kolibri-fleet-zio

## Getting an overview of effect composition in ZIO
The main concept to understand about ZIO is that every computation is constructed as descriptions
of the computation that need to be passed to a runtime to be executed.
As such it holds some similarities to frameworks such as Spark, where DAGs have to be run explicitly by an action method
on the dataframes.
And these descriptions are called effects, that are wrappers for any computation / function calls.
This is reflected in the main data type ZIO[R, E, A], where R stands for environment type (e.g environments provide
needed contextual data to execute the effect, such as DB connections; Any stands for no environment input needed to execute),
E stands for error type and A stands for the success type.

- Official doc: ```https://zio.dev/overview/getting-started```
- Example introducing some concepts step by step:
  ```https://scalac.io/blog/introduction-to-programming-with-zio-functional-effects/```

