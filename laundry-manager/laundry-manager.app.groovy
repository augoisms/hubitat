/**
 * Laundry Manager app for Hubitat
 *
 * Copyright (c) 2019 Justin Walker
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *
 *  v1.0.0 - Initial version
 *  v1.0.1 - Made washer, dryer, and reset button optional
 *
 */
definition(
    name: "Laundry Manager",
    namespace: "augoisms",
    author: "augoisms",
    description: "Monitor the laundry status and send a notification when the cycle is complete",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
	section("Select machines") {
		input "washerMeter", "capability.powerMeter", title: "Washer Power Meter"
        input "dryerMeter", "capability.powerMeter", title: "Dryer Power Meter"
		input "resetButton", "capability.pushableButton", title: "Reset Button"
	}
	section( "Notifications" ) {
		input "sendPushMessage", "enum", title: "Send a push notification?", options: ["Yes", "No"], required: false
		input "notificationDevice", "capability.notification", title: "Notification device", required: false
		input "repeat", "bool", title: "Repeat notifications?"
		input "repeatInterval", "number", title: "Repeat interval (minutes)", defaultValue: 15
        input "modes", "mode", title: "Only send in specific modes", multiple: true, required: false
    }
}

def installed() {
	init()
}

def updated() {
	unsubscribe()
	init()
}

def uninstalled() {
	removeChildDevices(getChildDevices())
}

def init() {
    if (washerMeter) {
        subscribe(washerMeter, "power", washerPowerHandler)
    }
    if (dryerMeter) {
        subscribe(dryerMeter, "power", dryerPowerHandler) 
    }
    if (resetButton) {
        subscribe(resetButton, "pushed", buttonHandler)
    }
	 
    createChildren()
}

///
/// management of child devices
///

def createChildren() {
    log.trace "creating Laundry Manager children"
    
    if (washerMeter) {
        createChild("laundry-machine-washer", "Laundry Washer")
    }
    
    if (dryerMeter) {
        createChild("laundry-machine-dryer", "Laundry Dryer")
    }
}

def createChild(dni, label) {
    def child = getChildDevice(dni)
    if (child) {
        log.warn "Device (${dni}) already exists"
        subscribeToChild(child)
	} else {
        try {
        	log.debug "attempting to create child device"
            def newChild = addChildDevice("augoisms", "Laundry Machine", dni, null, [name: label])
            log.trace "created ${newChild.displayName} with id $dni"
            subscribeToChild(newChild)
        }
        catch (Exception e) {
        	log.debug "error creating child device"
        	log.trace e
        }
    	
	}   
}

def subscribeToChild(child){    
    if (child.deviceNetworkId == "laundry-machine-washer") {
        subscribe(child, "status", washerStatusHandler)
        washerPowerHandler()
    }
    
    if (child.deviceNetworkId == "laundry-machine-dryer") {
        subscribe(child, "status", dryerStatusHandler)
        dryerPowerHandler()
    }
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}

///
/// event handlers
///

def buttonHandler(evt) {
	log.debug "button pushed"
    
    def washer = getChildDevice("laundry-machine-washer")
    if(washer.currentValue("status") == "finished") {
    	log.debug "resetting washer"
        washer.resetFinished()
    }
    
    def dryer = getChildDevice("laundry-machine-dryer")
    if(dryer.currentValue("status") == "finished") {
    	log.debug "resetting dryer"
        dryer.resetFinished()
    }
}

def washerStatusHandler(evt) {
	log.debug "checking washer status"  
    def washer = getChildDevice("laundry-machine-washer")
    statusCheck(washer, "Washer", "washerStatusHandler")
}

def dryerStatusHandler(evt) {
	log.debug "checking dryer status"   
    def dryer = getChildDevice("laundry-machine-dryer")
    statusCheck(dryer, "Dryer", "dryerStatusHandler")
}

def washerPowerHandler(evt) {
    def watts = washerMeter.currentValue("power")
    def washer = getChildDevice("laundry-machine-washer")
    washer.updatePower(watts)
}

def dryerPowerHandler(evt) {
    def watts = dryerMeter.currentValue("power")
    def dryer = getChildDevice("laundry-machine-dryer")
    dryer.updatePower(watts)
}

///
/// other methods
///

def statusCheck(device, deviceName, handlerName) {
	def status = device.currentValue("status")
  
    if (status == "finished") {
    	// send notification
        send("${deviceName} is finished")
        // schedule repeat notification
        if(repeat) {
        	log.debug "scheduling a repeat notification"
            runIn(repeatInterval * 60, handlerName)
        }
    }
}

private send(msg) {
	def modeOkay = !modes || modes.contains(location.mode)
    if (sendPushMessage != "No" && modeOkay) {
		log.debug("sending push message")
		notificationDevice.deviceNotification(msg)
	}
    
    log.debug msg
}