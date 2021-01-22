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
 *  v1.0.0 - Initial version
 *  v1.0.3 - Added support for time on threshold (2021-01-17).
 *  v1.0.6 - Removed min time on support (2021-01-21).
 *
 */
metadata {
    definition (name: 'Laundry Machine', namespace: 'augoisms', author: 'Justin Walker') {
        capability 'Sensor'
        capability 'Power Meter'
        capability 'Switch'
        capability 'Momentary'

        command 'refresh'
        
        attribute 'status', 'enum', ['idle', 'running', 'finished']
    }
}

preferences {
    input name: 'wattThreshold', type: 'number', title: '<b>Watt Threshold</b>', description: '<div><i>Minimum watt value to trigger state</i></div><br>', defaultValue: '10'
    // input name: 'minTimeOn', type: 'number', title: '<b>Time Threshold (On)</b>', description: '<div><i>Minimum time (seconds) watts need to stay above the threshold</i></div><br>', defaultValue: 15
    input name: 'minTimeOff', type: 'number', title: '<b>Time Threshold (Off)</b>', description: '<div><i>Minimum time (seconds) watts need to stay below the threshold</i></div><br>', defaultValue: 120

    input name: 'loggingEnabled', type: 'bool', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after 30 minutes.</i></div><br>', defaultValue: false
}

///
/// Required Methods
///

def installed() {
    installedUpdated()
}

def updated() {
    installedUpdated()
}

def installedUpdated() {
    unschedule()

    // disable logging in 30 minutes
    if (settings.loggingEnabled) runIn(1800, disableLogging)
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
	logDebug 'parseWattsForStatus()'

	// get the current status
	def currentStatus = device.currentValue('status') ?: '';
    // check if running to save for later
    if(currentStatus == 'running') {
        state.wasRunning = true;
    }

    logDebug "parseWattsForStatus() > currentStatus: ${currentStatus}";
    logDebug "parseWattsForStatus() > wasRunning: ${state.wasRunning}";
    logDebug "parseWattsForStatus() > watts: ${watts}";
    
    def isIdle = watts < wattThreshold;
    logDebug "parseWattsForStatus() > isIdle: ${isIdle}";
    
    // def minTimeOnMillis = (minTimeOn ?: 0) * 1000;
    // logDebug "parseWattsForStatus() > minTimeOnMillis: ${minTimeOnMillis}";

    def minTimeOffMillis = (minTimeOff ?: 0) * 1000;
    logDebug "parseWattsForStatus() > minTimeOffMillis: ${minTimeOffMillis}";

    def now = now()

    // check if we're *really* running
    // if (!isIdle && state.timeLastRunning) {
    //     // we have been running before
    //     // check if we have been running long enough
    //     logDebug "parseWattsForStatus() > now: ${now}, state.timeLastRunning: ${state.timeLastRunning} ";

    //     def lastHighDelta = (now - state.timeLastRunning)
    //     logDebug "parseWattsForStatus() > lastHighDelta: ${lastHighDelta}";

    //     if (lastHighDelta >= minTimeOnMillis) { 
    //     	// Power has been high for long enough
    //         // let isIdle remain false
    //         logDebug 'parseWattsForStatus() > Min on time has been met, go running'
    //         isIdle = false;
    //     } 
    //     else { 
    //     	// Has a low time but hasn't reached the minTimOn yet, re-schedule another check for the future
    //         // remain idle
    //         def reSchedTime = (minTimeOnMillis - lastHighDelta) / 1000
    //         logDebug "parseWattsForStatus() > Scheduling a refresh() in ${reSchedTime} seconds"
    //         runIn(reSchedTime.longValue() + 2, refresh);
    //         isIdle = true;
    //     }
    // }
    // else if(!isIdle) {
    //     // we're not idle (aka running) but we don't have a previous record of running
    //     // save the current time and ignore this report
    //     // remain idle
    //     logDebug 'parseWattsForStatus() > No previous running record, ignoring for now'
    //     state.timeLastRunning = now;
    //     isIdle = true;
        
    //     // check again in a few seconds incase we don't receive any more updates
    //     logDebug 'parseWattsForStatus() > Scheduling a refresh() in 5 seconds'
    //     runIn(5, refresh)
    // }
    // else {
    //     // we're idle, zero out lastTimeRunning
    //     state.timeLastRunning = 0
    // }
    
    // check if we're *really* idle
    if(isIdle && state.timeLastIdle) {
    	// we have been idle before
        // check if we have been idle long enough
        logDebug "parseWattsForStatus() > now(): ${now}";
        logDebug "parseWattsForStatus() > state.timeLastIdle: ${state.timeLastIdle} ";

        def lastLowDelta = (now - state.timeLastIdle)
        logDebug "parseWattsForStatus() > lastLowDelta: ${lastLowDelta}";

        if (lastLowDelta >= minTimeOffMillis) { 
        	// Power has been low for long enough
            // let isIdle remain true
            logDebug 'parseWattsForStatus() > Min off time has been met, go idle'
            isIdle = true;
        } 
        else { 
        	// Has a low time but isn't done yet, re-schedule another check for the future
            def reSchedTime = (minTimeOffMillis - lastLowDelta) / 1000
            logDebug "parseWattsForStatus() > Scheduling a refresh() in ${reSchedTime} seconds"
            runIn(reSchedTime.longValue() + 2, refresh);
            isIdle = false;
        }
    } 
    else if(isIdle) {
    	// we're idle but we don't have a previous record
        // save the current time and ignore this report
        // return last known/current status
        logDebug 'parseWattsForStatus() > No previous idle record, ignoring for now'
        state.timeLastIdle = now;
        isIdle = false;
        
        // check again in a few seconds incase we don't receive any more updates
        logDebug 'parseWattsForStatus() > Scheduling a refresh() in 5 seconds'
        runIn(5, refresh)
    }
    else {
    	// we're not idle, zero out lastTimeIdle
        state.timeLastIdle = 0;
    }
    
    logDebug "parseWattsForStatus() > isIdle after checks: ${isIdle}";
    
    def finalState = '';
    if(isIdle && state.wasRunning) {
        finalState = 'finished'
        sendEvent(name: 'status', value: finalState)
        switchOn()
    } else if (isIdle) {
        finalState = 'idle'
        sendEvent(name: 'status', value: finalState)
        switchOff()
    } else {
        finalState = 'running'
        sendEvent(name: 'status', value: finalState)
        switchOff()
    }
    
    logDebug "parseWattsForStatus() > finalState: ${finalState}";
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
    if (loggingEnabled) {
        log.debug str
    }
}

void disableLogging() {
	log.info 'Logging disabled.'
	device.updateSetting('loggingEnabled',[value:'false',type:'bool'])
}

///
/// public methods that can be called by the parent
///

public updatePower(watts) {
    sendEvent(name: 'power', value: watts, unit: 'W')
    parseWattsForStatus(watts)
}
