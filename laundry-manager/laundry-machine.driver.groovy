/**
 * Laundry Machine driver for Hubitat, for use with Laundry Manager app
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
 *  V1.0.0 - Initial version
 *
 */
metadata {
    definition (name: "Laundry Machine", namespace: "augoisms", author: "Justin Walker") {
        capability "Sensor"
        capability "Power Meter"
        capability "Switch"
        capability "Momentary"

        command "refresh"
        
        attribute "status", "enum", ["idle", "running", "finished"]
        attribute "wasRunning", "enum", ["true", "false"]
    }
}

preferences {
    input title: "", description: "State related options", type: "paragraph", element: "paragraph"
    input name: "wattThreshold", type: "number", title: "Minimum watt value to trigger state\n", defaultValue: "10"
    input name: "minTimeOff", type: "number", title: "Minimum time (min) watts need to stay below the threshold\n", defaultValue: "2"

    input title: "", description: "Logging", type: "paragraph", element: "paragraph"
    input name: "isLogLevelDebug", type: "bool", title: "Show debug log level ?\n", defaultValue: "false"
}

///
/// Required Methods
///

def installed() {
    
}

def on() {
    push()
}

def off() {
    sendEvent(name: "switch", value: "off")
    resetFinished()
}

def push() {
    sendEvent(name: "switch", value: "on")
    runIn(3, off)
}

def refresh() {
    def watts = device.currentValue("power")
    parseWattsForStatus(watts)
}

///
/// Custom Methods
///

def parseWattsForStatus(watts) {
	logDebug "parseWattsForStatus()"
	// get the current status
	def currentStatus = device.currentValue("status") ?: "";
    // check if running to save for later
    if(currentStatus == "running") {
        state.wasRunning = true;
    }
    logDebug "parseWattsForStatus() > wasRunning: ${state.wasRunning}";
    
	logDebug "parseWattsForStatus() > checking watt value";
    logDebug "parseWattsForStatus() > watts: " + watts;
    logDebug "parseWattsForStatus() > currentStatus: ${currentStatus}";
    
    def isIdle = watts < wattThreshold;
    logDebug "parseWattsForStatus() > isIdle: " + isIdle;
    
    def minOffTimeMillis = minTimeOff * 60 * 1000;
    logDebug "parseWattsForStatus() > minOffTimeMillis: " + minOffTimeMillis;
    
    // check if we're *really* idle
    if(isIdle && state.timeLastIdle) {
    	// we have been idle before
        // check if we have been idle long enough
        logDebug "parseWattsForStatus() > now(): " + now();
        logDebug "parseWattsForStatus() > state.timeLastIdle: " + state.timeLastIdle;
        def lastLowDelta = (now() - state.timeLastIdle)
        logDebug "parseWattsForStatus() > lastLowDelta: " + lastLowDelta;
        if (lastLowDelta >= minOffTimeMillis) { 
        	// Power has been low for long enough
            // let isIdle remain true
            logDebug "parseWattsForStatus() > Min off time has been met, let's go idle"
            isIdle = true;
        } else { 
        	// Has a low time but isn't done yet, re-schedule another check for the future
            def reSchedTime = (minOffTimeMillis - lastLowDelta) / 1000
            logDebug "parseWattsForStatus() > Scheduling a refresh() in ${reSchedTime} seconds"
            runIn(reSchedTime.longValue() + 2, refresh);
            isIdle = false;
        }
    } else if(isIdle) {
    	// we're idle but we don't have a previous record
        // save the current time and ignore this report
        // return last known/current status
        logDebug "parseWattsForStatus() > No previous idle record, ignoring for now"
        state.timeLastIdle = now();
        isIdle = false;
        
        // check again in a few seconds incase we don't receive any more updates
        logDebug "parseWattsForStatus() > Scheduling a refresh() in ${minTimeOff} seconds"
        runIn(minTimeOff + 2, refresh)
    } else {
    	// we're not idle, zero out lastTimeIdle
        state.timeLastIdle = false;
    }
    
    logDebug "parseWattsForStatus() > isIdle after checks: " + isIdle;
    
    def finalState = "";
    if(isIdle && state.wasRunning) {
        finalState = "finished"
        sendEvent(name: "status", value: finalState)
        switchOn()
    } else if (isIdle) {
        finalState = "idle"
        sendEvent(name: "status", value: finalState)
        switchOff()
    } else {
        finalState = "running"
        sendEvent(name: "status", value: finalState)
        switchOff()
    }
    
    logDebug "parseWattsForStatus() > finalState: " + finalState;
}
            
def switchOn() {
    if (device.currentValue("switch") != "on") {   
        sendEvent(name: "switch", value: "on")
    }
}

def switchOff() {
    if (device.currentValue("switch") != "off") {   
        sendEvent(name: "switch", value: "off")
    }
}

def resetFinished() {    
	state.wasRunning = false;
    logDebug "resetFinished() > wasRunning: ${state.wasRunning}";
    refresh();
}

void logDebug(str) {
    if (isLogLevelDebug) {
        log.debug str
    }
}

///
/// public methods that can be called by the parent
///

public updatePower(watts) {
    sendEvent(name: 'power', value: watts, unit: 'W')
    parseWattsForStatus(watts)
}
