# Laundry Manager for Hubitat

Monitors the power usage from outlets that the washer and dryer are plugged into and creates child devices with statuses (idle, running, & finished) based on usage. Can be configured to send you a notification, with repeats, when either are `finished`. The status can be reset from finished to idle by subscribing to a button or by pressing/toggling the momentary switch that the child device provides.

Installation
============

1. Install `laundry-manager.groovy` app
2. install `laundry-machine.groovy` driver
3. Add user installed app "Laundry Manager"
4. Select outlets for washer and dryer