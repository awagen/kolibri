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

## Fibers and interrupting (https://zio.dev/reference/interruption/)
Reference to a fiber runtime can be created by calling one of the variants of ```.fork``` on a created
effect. A fiber provides an ```.interrupt``` method, which in general allows interrupting any fiber.
Yet that does not mean the computation (effect) wrapped into the ZIO.effect from which the fiber reference
was retrieved is also interrupted. 
That is, if we call interrupt on a fiber that wraps a blocking operation such as ```Thread.sleep```,
the blocking operation will just go on (and make your tests based on assuming interruptibility take 
a long time). Options allowing interruption of the underlying effect are:
- ```.attemptBlockingInterrupt```, such as in
```
fiber <- ZIO.attemptBlockingInterrupt({
  Thread.sleep(10000)
}).fork
```
This option comes with overhead, thus if performance is prio, this needs to be considered.
Also, it won't work on executions that catch and ignore ```InterruptedException```.
- ```.attemptBlockingCancelable```, such as in
```
case class CancellableTestWait() {
  private[this] val runFlag = new AtomicReference[Boolean](true)

  def run(): Unit = {
    while (runFlag.get()) {
      try Thread.sleep(5)
      catch {
        case _: InterruptedException =>
          println("Ignoring InterruptedException")
          ()
      }
    }
  }

  def stop(): Unit = {
    println("Stopping running task")
    runFlag.getAndSet(false)
    ()
  }
}
.....
for {
  service <- ZIO.attempt(CancellableTestWait())
  fiber <- ZIO.attemptBlockingCancelable(effect = {
    service.run()
  })(cancel = ZIO.succeed(service.stop()))
    .fork
} yield ()
```
This comes with the drawback that we have to implement the cancelling manually, such as 
by changing an atomic reference value.
