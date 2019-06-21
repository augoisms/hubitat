 /**
 *  Rainforest Eagle Manager - A service manager for the rainforest handler
 *
 *  Copyright 2017 Justin Walker
 *
 *  heavily adapted from wattvision manager
 *  https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/master/smartapps/smartthings/wattvision-manager.src/wattvision-manager.groovy
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
 
definition(
	name: "Rainforest Manager",
	namespace: "augoisms",
	author: "Justin Walker",
	description: "Monitor your whole-house energy use by connecting to your Rainforest Eagle",
	iconUrl: "http://cdn.device-icons.smartthings.com/Lighting/light14-icn@2x.png",
	iconX2Url: "http://cdn.device-icons.smartthings.com/Lighting/light14-icn@2x.png",
    singleInstance: true
)

preferences {
    section("Use the values printed on the bottom of your Eagle"){
        input name: "macId", type: "text", title: "MAC Address (hex)", required: true, displayDuringSetup: true
    	input name: "cloudId", type: "text", title: "Cloud ID", required: true, displayDuringSetup: true
    }
    section("Eagle must have a static IP, default port is 80"){
        input name: "theAddr", type: "string", title: "ip:port", multiple: false, required: true, displayDuringSetup: true
    }
    section("Automatically power cycle device when it starts to return errors"){
        input name: "powerCycleEnabled", type: "bool", title: "Enabled?", defaultValue: false
        input name: "powerCycleSwitch", type: "capability.switch", title: "Switch that the Eagle is plugged into"
    }
}

def installed() {
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	unschedule()
	initialize()
}


def initialize() {
	createChild()
}

// ========================================================
// SmartThings initiated setup
// ========================================================

def createChild() {
	log.trace "creating RainforestEagle child device"

    
    def parts = theAddr.split(":")
    def iphex = convertIPtoHex(parts[0])
    def porthex = convertPortToHex(parts[1])
    def dni = "$iphex:$porthex"
    log.debug "dni: $dni"
    // save dni for later use
    state.dni = dni
    
    def child = getChildDevice(dni)
    
    if (child) {
		log.warn "Device already exists"
        subscribeToChild(child)
	} else {
        try {
        	log.debug "attempting to create child device"
			def newChild = addChildDevice("augoisms", "RainforestEagle", "${dni}")
			// for some reason adding the map causes method signature error
			//def newChild = addChildDevice("augoisms", "RainforestEagle", "${dni}", [name: "RainforestEagle", label: "Power Meter", isComponent: true])
            log.trace "created ${newChild.displayName} with id $dni"
            subscribeToChild(child)
        }
        catch (Exception e) {
        	log.debug "error creating child device"
        	log.trace e
        }
    	
	}
}

def subscribeToChild(child){
    // only subscribe if we've enabled auto power cycle
    if (powerCycleEnabled) {
        subscribe(child, "power", powerHandler)   
    }
}

def powerHandler(evt) {
    def currentPower = evt.value
    //log.debug "Current Power: ${currentPower}"
    
    if (currentPower == "---") {
        log.debug "Eagle is returning errors"
        // only power cycle if we've previously received a successful value
        // this will prevent an endless loop as the device takes time to boot up
        if (state.successReceived == true) {
            log.debug "power cycling device"
            state.successReceived = false
            powerCycleOff()
            // wait 5 seconds then turn it back on
            runIn(5, powerCycleOn)
        }
    } else {
        // we received a successful result, reset flag
        state.successReceived = true
    }
}

def powerCycleOn() {
    log.debug "turning on"
    powerCycleSwitch.on()
}

def powerCycleOff() {
    log.debug "turning off"
    powerCycleSwitch.off()
}

// ========================================================
// public methods for child device to call
// ========================================================

public getSettings(child) {
	//log.debug "getSettings()"
    return settings
}

// ========================================================
// private helper methods
// ========================================================

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize(".").collect {  String.format("%02X", it.toInteger() ) }.join()
    log.debug "IP address entered is $ipAddress and the converted hex code is $hex"
    return hex
}

private String convertPortToHex(port) {
    String hexport = port.toString().format("%04X", port.toInteger() )
    log.debug hexport
    return hexport
}