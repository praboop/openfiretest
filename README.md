# Test Openfire under load.

Generates load to openfire and you can validate CPU, memory, event processing etc.

# MissionControl

Start this service which connects publisher and consumers.
Once started, access it via http://<localhost:8080/

# Subscriber

Starts a set of consumers which will receive events from openfire.

# Publisher

Starts a single publisher which will send events to the consumer via admin node.


## Note:

Configurations are in Mission Control.