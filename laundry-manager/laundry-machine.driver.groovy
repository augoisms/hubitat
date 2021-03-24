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
 *  v1.0.7 - Added min time on support (2021-03-21).
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
    input name: 'minTimeOn', type: 'number', title: '<b>Time Threshold (On)</b>', description: '<div><i>Minimum time (seconds) watts need to stay above the threshold</i></div><br>', defaultValue: 15
    input name: 'minTimeOff', type: 'number', title: '<b>Time Threshold (Off)</b>', description: '<div><i>Minimum time (seconds) watts need to stay below the threshold</i></div><br>', defaultValue: 120

    input name: 'loggingDuration', type: 'enum', title: '<b>Enable Logging?</b>', description: '<div><i>Automatically disables after selected time.</i></div><br>', options: [0: 'Disabled', 1800: '30 Minutes', 3600: '1 Hour', 86400: '24 Hours'], defaultValue: 0
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
    state.clear()

    // disable logging automatically
    Long loggingDuration = settings.loggingDuration as Long
    if (loggingDuration > 0) runIn(loggingDuration, disableLogging)
}

def on() {
    push()
}

def off() {
    sendEvent(name: 'switch', value: 'off')
    resetFinished()
}

def push() {
    sendEvent(name: 'switch', value: 'on')
    runIn(3, off)
}

def refresh() {
    def watts = device.currentValue('power')
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
    
    Boolean isIdle = watts < wattThreshold;
    logDebug "parseWattsForStatus() > watts: ${watts}, isIdle: ${isIdle}, currentStatus: ${currentStatus}, wasRunning: ${state.wasRunning}";
    
    def minTimeOnMillis = (minTimeOn ?: 0) * 1000;
    def minTimeOffMillis = (minTimeOff ?: 0) * 1000;
    logDebug "parseWattsForStatus() > minTimeOn: ${minTimeOnMillis}ms, minTimeOff: ${minTimeOffMillis}ms";

    def now = now();
    Boolean finalIdle = false;

    // check if we're *really* running
    if (!isIdle && state.timeFirstRunning) {
        // we have been running before
        // check if we have been running long enough
        logDebug "parseWattsForStatus() > now: ${now}, state.timeFirstRunning: ${state.timeFirstRunning} ";

        def lastHighDelta = (now - state.timeFirstRunning)
        logDebug "parseWattsForStatus() > lastHighDelta: ${lastHighDelta}";

        if (lastHighDelta >= minTimeOnMillis) { 
            // Power has been high for long enough
            // let isIdle remain false
            logDebug 'parseWattsForStatus() > Min time on has been met, go running'
            finalIdle = false;
        } 
        else { 
            // Has a low time but hasn't reached the minTimOn yet, re-schedule another check for the future
            // remain idle
            def reSchedTime = (minTimeOnMillis - lastHighDelta) / 1000
            logDebug "parseWattsForStatus() > Min time on has not been met. Scheduling a refresh() in ${reSchedTime} seconds"
            runIn(reSchedTime.longValue() + 2, refresh);
            finalIdle = true;
        }
    }
    else if(!isIdle) {
        // we're not idle (aka running) but we don't have a previous record of running
        // save the current time and ignore this report
        // remain idle
        logDebug 'parseWattsForStatus() > No previous running record, ignoring for now'
        state.timeFirstRunning = now;
        finalIdle = true;
        
        // check again in a few seconds incase we don't receive any more updates
        logDebug 'parseWattsForStatus() > Scheduling a refresh() in 5 seconds'
        runIn(5, refresh)
    }
    
    // stay idle if we're already idle
    if (isIdle && currentStatus == 'idle') {
        finalIdle = true;
        if (!state.timeFirstIdle) {
            state.timeFirstIdle = now;
        }
    }
    // check if we're *really* idle
    else if(isIdle && state.timeFirstIdle) {
        // we have been idle before
        // check if we have been idle long enough
        logDebug "parseWattsForStatus() > now: ${now}, state.timeFirstIdle: ${state.timeFirstIdle} ";

        def lastLowDelta = (now - state.timeFirstIdle)
        logDebug "parseWattsForStatus() > lastLowDelta: ${lastLowDelta}";

        if (lastLowDelta >= minTimeOffMillis) { 
            // Power has been low for long enough
            // let isIdle remain true
            logDebug 'parseWattsForStatus() > Min time off has been met, go idle'
            finalIdle = true;

            // since we're idle, zero out timeFirstRunning
            state.timeFirstRunning = 0;
        } 
        else { 
            // Has a low time but isn't done yet, re-schedule another check for the future
            def reSchedTime = (minTimeOffMillis - lastLowDelta) / 1000
            logDebug "parseWattsForStatus() > Min time off has not been met. Scheduling a refresh() in ${reSchedTime} seconds"
            runIn(reSchedTime.longValue() + 2, refresh);
            finalIdle = false;
        }
    } 
    else if(isIdle) {
        // we're idle but we don't have a previous record
        // save the current time and ignore this report
        // return last known/current status
        logDebug 'parseWattsForStatus() > No previous idle record, ignoring for now'
        state.timeFirstIdle = now;
        finalIdle = false;
        
        // check again in a few seconds incase we don't receive any more updates
        logDebug 'parseWattsForStatus() > Scheduling a refresh() in 5 seconds'
        runIn(5, refresh)
    }
    else {
        // we're not idle, zero out lastTimeIdle
        state.timeFirstIdle = 0;
    }
    
    String finalState = '';
    if(finalIdle && state.wasRunning) {
        finalState = 'finished'
        sendEvent(name: 'status', value: finalState)
        switchOn()
    } else if (finalIdle) {
        finalState = 'idle'
        sendEvent(name: 'status', value: finalState)
        switchOff()
    } else {
        finalState = 'running'
        sendEvent(name: 'status', value: finalState)
        switchOff()
    }
    
    logDebug "parseWattsForStatus() > findleIdle: ${finalIdle}, finalState: ${finalState}";
}
            
def switchOn() {
    if (device.currentValue('switch') != 'on') {   
        sendEvent(name: 'switch', value: 'on')
    }
}

def switchOff() {
    if (device.currentValue('switch') != 'off') {   
        sendEvent(name: 'switch', value: 'off')
    }
}

def resetFinished() {    
    state.wasRunning = false;
    logDebug "resetFinished() > wasRunning: ${state.wasRunning}";
    refresh();
}

void logDebug(str) {
    Long loggingDuration = settings.loggingDuration as Long
    if (loggingDuration > 0) {
        log.debug str
    }
}

void disableLogging() {
    log.info 'Logging disabled.'
    device.updateSetting('loggingDuration',[value: '0',type:'enum'])
}

///
/// public methods that can be called by the parent
///

public updatePower(watts) {
    sendEvent(name: 'power', value: watts, unit: 'W')
    parseWattsForStatus(watts)
}
