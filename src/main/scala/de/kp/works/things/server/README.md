
# Things

**Things** is an Akka based standalone HTTP(s) server that manages the interaction
between real-time data sources, and the **ThingsBoard** IoT platform (community edition).

Actually supported data flows begin at

* EEA Air Quality service,
* OpenWeather, and
* The Things Network,

are transformed into ThingsBoard's MQTT telemetry format and are sent to ThingsBoard's MQTT
broker. **Things** is based on the PredictiveWorks. software stack, and is designed to integrate
further data sources (and also sinks) with ease.
