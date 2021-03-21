# Kolibri Datatypes
![Alt text](images/kolibri.svg?raw=true "Kolibri Datatypes")

# Build
- ```sbt clean assembly```
  - this puts the respective jar within target/scala-x.xx/ (with the name as provided in 'assemblyJarName in assembly' 
  property in sbt) (https://github.com/sbt/sbt-assembly)
  
# Publish Locally
- ```sbt publishLocal```