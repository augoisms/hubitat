definition(
    name: "Open The Windows",
    namespace: "augoisms",
    author: "augoisms",
    description: "Monitor the inside & outside temperature and when the outside temp drops below the inside temp send a notification.",
    category: "Convenience",
    iconUrl: "http://cdn.device-icons.smartthings.com/Home/home1-icn@2x.png",
    iconX2Url: "http://cdn.device-icons.smartthings.com/Home/home1-icn@2x.png"
)

preferences {
	section("Monitor the temperature") {
		input "tempSensorInside", "capability.temperatureMeasurement", title: "Inside Temp"
        input "tempSensorOutside", "capability.temperatureMeasurement", title: "Outside Temp"
	}
	section("When to trigger notification") {
		input "tempDelta", "number", title: "Temperature delta"
        input "afterTimeInput", "time", title: "After this time", required: true
		input "notifyClose", "bool", title: "Also notify to close the windows?"
	}
    section( "Send notifications to" ) {
		input "sendPushMessage", "enum", title: "Send a push notification?", options: ["Yes", "No"], required: false
		input "notificationDevice", "capability.notification", title: "Notification device", required: false
    }
    section("Then flash..."){
		input "switches", "capability.switch", title: "These lights", multiple: true, required: false
		input "numFlashes", "number", title: "This number of times (default 3)", required: false
	}
	section("Time settings in milliseconds (optional)..."){
		input "onFor", "number", title: "On for (default 1000)", required: false
		input "offFor", "number", title: "Off for (default 1000)", required: false
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
	subscribe(tempSensorInside, "temperature", temperatureHandler)
    subscribe(tempSensorOutside, "temperature", temperatureHandler)
	schedule("0 0 0 * * ?", midnightReset)
    midnightReset()
}

def temperatureHandler(evt) {
	log.trace "temperature: $evt.value, $evt"
    
    def insideTemp = tempSensorInside.currentTemperature
    log.debug "Inside temp: ${insideTemp}"
    def outsideTemp = tempSensorOutside.currentTemperature
    log.debug "Outside temp: ${outsideTemp}"
    
    // first validate the outside temp
    // wunderground will often send incorrect data so if the difference
    // between the last update is too large, let's just ignore it
    def lastOutsideTemp = state.lastOutsideTemp ?: outsideTemp    
    if(Math.abs(lastOutsideTemp - outsideTemp) > 5 ) {	
		log.debug "outside temp has changed more than 5 degrees. Ignoring"
    	return
    }
	// save outside temp for future reference
    state.lastOutsideTemp = outsideTemp
    
    // calculate the temp difference
    def tempDiff = insideTemp - outsideTemp
    
    // calculate the time
    def afterTimeToday = timeToday(afterTimeInput)
    def isAfterTime = now() > afterTimeToday.time

	// If the diff is ge to the delta & it's after the set time send a notification
	if (tempDiff >= tempDelta && isAfterTime) {
		log.debug "Temperature is below the threshold"

		if (state.notificationOpenWindowsSent) {
			log.debug "Notification already sent today"
		} else {
			log.debug "Temperature delta is >= $tempDelta:  sending notification"
			def tempScale = location.temperatureScale ?: "F"
            // send the message
			send("It's ${outsideTemp}º${tempScale} outside. Open the windows", "notificationOpenWindowsSent")
            // flash the lights
			flashLights()
		}
	}

	// check if the inside temp has reached the outside temp and notify to close the windows
	// don't send if we've already notified to open the windows
	if (outsideTemp >= insideTemp) {
		log.debug "Inside temp is now at or above outside temp"

		if(!notifyClose || state.notificationOpenWindowsSent || state.notificationCloseWindowsSent) {
			log.debug "Notification already sent today or is disabled"
		} else {
			log.debug "Sending close windows notification"
			def tempScale = location.temperatureScale ?: "F"
            // send the message
			send("It's ${insideTemp}${tempScale} inside and ${outsideTemp}${tempScale} outside. Close the windows", "notificationCloseWindowsSent")
            // flash the lights
			flashLights()
		}
	}
}

private send(msg, stateKey) {

	if (sendPushMessage != "No") {
		log.debug("sending push message")
		notificationDevice.deviceNotification(msg)
	}
    
    // set state variable and reset at midnight
    state[stateKey] = true;
    //runOnce(getMidnight(), midnightReset)

    log.debug msg
}

def midnightReset() {
	log.debug "midnightReset()"
	state.notificationOpenWindowsSent = false;
	state.notificationCloseWindowsSent = false;
}

def getMidnight() {
	def midnightToday = timeToday("2000-01-01T23:59:59.999-0000", location.timeZone)
    log.debug "midnight: ${midnightToday.time}"
	return midnightToday
}

private flashLights() {
	def doFlash = true
	def onFor = onFor ?: 1000
	def offFor = offFor ?: 1000
	def numFlashes = numFlashes ?: 3

	log.debug "LAST ACTIVATED IS: ${state.lastActivated}"
	if (state.lastActivated) {
		def elapsed = now() - state.lastActivated
		def sequenceTime = (numFlashes + 1) * (onFor + offFor)
		doFlash = elapsed > sequenceTime
		log.debug "DO FLASH: $doFlash, ELAPSED: $elapsed, LAST ACTIVATED: ${state.lastActivated}"
	}
    
    if(switches == null) {
    	doFlash = false
    }

	if (doFlash) {
		log.debug "FLASHING $numFlashes times"
		state.lastActivated = now()
		log.debug "LAST ACTIVATED SET TO: ${state.lastActivated}"
		def initialActionOn = switches.collect{it.currentSwitch != "on"}
		def delay = 0L
		numFlashes.times {
			log.trace "Switch on after  $delay msec"
			switches.eachWithIndex {s, i ->
				if (initialActionOn[i]) {
					s.on(delay: delay)
				}
				else {
					s.off(delay:delay)
				}
			}
			delay += onFor
			log.trace "Switch off after $delay msec"
			switches.eachWithIndex {s, i ->
				if (initialActionOn[i]) {
					s.off(delay: delay)
				}
				else {
					s.on(delay:delay)
				}
			}
			delay += offFor
		}
	}
}