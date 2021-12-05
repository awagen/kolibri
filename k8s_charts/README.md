## Kustomize Charts

The example files in base and overlay subfolders contain the yaml definitions needed for deployment of the 
webserver, the compute nodes and the ui (Kolibri Watch). The definitions assume deployment in google cloud,
yet the environment specific parts are few (mainly referring to the credential mount, which is very
similar procedure in AWS).
Some values need substituting with your environment specific values, and those values are marked with an TODO
at the moment. Thus before using them to deploy the stack, make sure to replace the values marked with TODO.
A script to generate the scripts you need for your env is provided in the kustomize folder (```copyAndFillIn.sh```).
This allows you to enter the values that hold for you and on execution replaces the placeholders in the templates with 
the values you defined in the script to provide executable deployment scripts.

## Helm Charts
See an example in the project root's k8s_charts/helm/charts folder, which contains example helm charts.
Those are written to satisfy the need of starting up a local kind-cluster. Corresponding startup scripts
for kind cluster with docker registry available within the kind cluster and service installation are provided in the 
k8s_charts/helm/scripts folder. The charts can be easily adjusted by modifying the config values defined in values.yaml.
The setup defined in the charts correspond to above kustomize setup case.

## Notes
- the provided helm and kustomize charts might deviate at some point, at which the kustomize charts shall be seen as example,
while the helm charts are to be seen as reference.