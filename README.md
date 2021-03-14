# ecobee-exporter 

This is a basic prometheus exporter to retrieve data from your ecobee smart thermostat and sensors.

The auth flow from api key to retrieving the refresh token is a work in progress.
Follow the guide here to retrieve the refresh and access token and set them as 
env variables 

``` sh
API_KEY={API_KEY}
REFRESH_TOKEN={REFRESH_TOKEN}
```


## Generating the jar

``` sh
clojure -M:uberdeps
```


## Executing the jar

Ensure the required environment variables are set
``` sh
java -cp target/ecobee-exporter.jar clojure.main -m collector.core
```

Or as docker 

``` sh
docker build -t ecobee-exporter .
docker run -it -p 8080:3000 -e API_KEY=$API_KEY -e REFRESH_TOKEN=$REFRESH_TOKEN ecobee-exporter
```


## Building multi-arch docker containers
Since this project primarily runs on a raspberry pi cluster.

``` sh
docker buildx build --push --platform linux/arm64,linux/amd64 --tag {dockeraccount}/ecobee-exporter:latest .
```
