### Steps
- install docker
- install kubectl
- install kubens (namespace management): https://github.com/ahmetb/kubectx#installation
    - Linux: ```sudo apt install kubectx```
    - or: 
  ``` 
      wget https://raw.githubusercontent.com/ahmetb/kubectx/master/kubectx
      wget https://raw.githubusercontent.com/ahmetb/kubectx/master/kubens
      chmod +x kubectx kubens
      sudo mv kubens kubectx /usr/local/bin
  ```
- install helm (https://helm.sh/docs/intro/install/):
  - e.g via install script:
  ```
  curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3
  chmod 700 get_helm.sh
  ./get_helm.sh
  ```
- create cluster: ```kind create cluster --name kind1```
- kolibri namespace: ```kubectl create namespace kolibri```
- switching to namespace: ```kubens kolibri```
- installing helm chart for httpserver: 
  ```
  helm install kolibri-cluster --debug ./kolibri-service
  ```
- uninstall httpserver install: ```helm uninstall kolibri-cluster```
- delete kind cluster: ```kind delete cluster```


### Use local registry with kind
As documented here https://kind.sigs.k8s.io/docs/user/local-registry/
as of now local docker registry has to be created and images pushed to 
that specific registry. The script located at ```./scripts/kind-with-registry.sh```
reflects the script provided in the above link and sets up a local registry
for kind and starts kind cluster with that registry enabled.
After it is executed, images can be pushed and used as follows:
- (Optional) docker pull (random example image), e.g : ```docker pull gcr.io/google-samples/hello-app:1.0```
- tagging image to use local kind registry: ```docker tag gcr.io/google-samples/hello-app:1.0 localhost:5000/hello-app:1.0```
- push image to local kind registry: ```docker push localhost:5000/hello-app:1.0```
- image can then be used as follows: ```kubectl create deployment hello-server --image=localhost:5000/hello-app:1.0```
- any local image tagged for the new registry and pushed there 
  can be used within the kind deployment, e.g for image tagged and pushed as localhost:5000/image:foo
  can be used within deployment as image localhost:5000/image:foo.
  
In our case (substitute version accordingly to the version used in docker image tag):
- ./scripts/kind-with-registry.sh (needed once to create repo and start kind cluster with repo enabled)
- Then we tag and push push all images to make them available within kind registry:
  - ```sudo docker tag kolibri-base:0.1.0-beta5 localhost:5000/kolibri-base:0.1.0-beta5```
  - ```sudo docker tag response-juggler:0.1.0 localhost:5000/response-juggler:0.1.0```
  - ```sudo docker push localhost:5000/kolibri-base:0.1.0-beta5```
  - ```sudo docker push localhost:5000/response-juggler:0.1.0```
- create namespace: ```sudo kubectl create namespace kolibri```  
- switch namespace: ```sudo kubens kolibri```  
- install kolibri service: ```sudo helm install kolibri-cluster --debug ./kolibri-service```
- install response-juggler: ```sudo helm install response-juggler --debug ./response-juggler```
- uninstall service: ``` sudo helm uninstall kolibri-cluster```
- uninstall response-juggler: ``` sudo helm uninstall response-juggler```
- to be able to access apps from local host, use port forwarding: 
  - service: ```sudo kubectl port-forward [kolibri-service-httpserver-pod-name] 80:8000```
  - juggler: ```sudo kubectl port-forward [juggler-podName] 81:80```
so ``` curl localhost:80/hello``` should provide a response (see: https://kubernetes.io/docs/tasks/access-application-cluster/port-forward-access-application-cluster/)
  
  
### Using hpa locally
- to allow hpa to pickup pod metrics locally, metrics server needs to be installed:
https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale-walkthrough/