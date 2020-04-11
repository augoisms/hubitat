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
 *  1.0.3 - Added TTS notifications, speaker selection, volume/restore, restrictions seperation between pushover and TTS, change status back to Idle after a given amount of time
 *  1.0.2 - Allow multiple notification devices
 *  1.0.1 - Made washer, dryer, and reset button optional
 *  1.0.0 - Initial version
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
	section("Select Machines:") {
		input "washerMeter", "capability.powerMeter", title: "Washer Power Meter"
        input "dryerMeter", "capability.powerMeter", title: "Dryer Power Meter"
		input "resetButton", "capability.pushableButton", title: "Reset Button"
        input "resetAuto","bool", defaultValue: false, title: "Auto-reset machines back to idle?"
        if(resetAuto) input "resetMin", defaultValue: "60", title: "Number of minutes before reseting?"
	}
	section( "Notifications:" ) {
        input(name: "speechmode", type: "bool", defaultValue: "false", title: "Use Speech Speaker(s) for TTS?", description: "Speech Speaker(s)?", submitOnChange: true)
        if (speechmode) {
            input "speechspeaker", "capability.speechSynthesis", title: "Choose speaker(s)", required: false, multiple: true, submitOnChange: true
          	// Master Volume settings
			input "speakervolume", "number", title: "Notification Volume Level:", description: "0-100%", required: false, defaultValue: "75", submitOnChange: true
			input "speakervolRestore", "number", title: "Restore Volume Level:", description: "0-100", required: false, defaultValue: "60", submitOnChange: true
        }
		input "sendPushMessage", "enum", title: "Send push notifications?", options: ["Yes", "No"], required: false
        if(sendPushMessage)	input "notificationDevices", "capability.notification", title: "Notification device(s)", required: false, multiple: true
		input "repeat", "bool", title: "Repeat notifications?"
		if(repeat) input "repeatInterval", "number", title: "Repeat interval (minutes)", defaultValue: 15
    }
    section("Restrictions:") {
        
       input "modes", "mode", title: "Pushover Notifications only send during specific modes?", multiple: true, required: false
       input "ttsmodes", "mode", title: "TTS Notifications only send during specific modes?", multiple: true, required: false
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
    if(washer.currentValue("status") == "Finished") {
    	log.debug "resetting washer"
        washer.resetFinished()
    }
    
    def dryer = getChildDevice("laundry-machine-dryer")
    if(dryer.currentValue("status") == "Finished") {
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
  
    if (status == "Finished") {
    	// send notification
        send("The ${deviceName} has finished a load of laundry.")
        if(resetAuto) {
            runIn(resetMin.toInteger()*60, dryer.resetFinished)
            runIn(resetMin.toInteger()*60, washer.resetFinished)
        }
        // schedule repeat notification
        if(repeat) {
        	log.debug "scheduling a repeat notification"
            runIn(repeatInterval * 60, handlerName)
        }
    }
}

private send(msg) {
    def modeTTSOkay = !ttsmodes || ttsmodes.contains(location.mode)
	def modeOkay = !modes || modes.contains(location.mode)
    if (sendPushMessage != "No" && modeOkay) {
		log.debug("sending push message")		
        notificationDevices.each{ device -> 
            device.deviceNotification(msg)
        }
	}
    if (speechmode != "No" && modeTTSOkay) {
			try {
                speechspeaker.initialize() 
                if (logEnable) log.debug "Initializing Speech Speaker"
                pauseExecution(2500)
            }
            catch (any) { if(logEnable) log.debug "Speech device doesn't support initialize command" }
            try { 
                speechspeaker.setVolume(speakervolume)
                if (logEnable) log.debug "Setting Speech Speaker to volume level: ${speakervolume}"
				pauseExecution(2000)
            }
            catch (any) { if (logEnable) log.debug "Speech speaker doesn't support volume level command" }
                
			if (logEnable) log.debug "Sending alert to Google and Speech Speaker(s)"
            alertmsg = alertmsg.toLowerCase()
            speechspeaker.speak(msg)
            
            try {
				if (speakervolRestore) {
					pauseExecution(atomicState.speechDuration2)
					speechspeaker.setVolume(speakervolRestore)	
                    if (logEnable) log.debug "Restoring Speech Speaker to volume level: ${speakervolRestore}"
                }
            }
                catch (any) { if (logEnable) log.debug "Speech speaker doesn't support restore volume command" }
    }
    log.debug msg
}
