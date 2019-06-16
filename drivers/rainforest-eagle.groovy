/**
 *  Rainforest Eagle - Device handler, requires Rainforest Eagle Manager
 *
 *  Copyright 2017 Justin Walker
 *
 *  heavily adapted from wattvision device type
 *  https://github.com/SmartThingsCommunity/SmartThingsPublic/blob/master/devicetypes/smartthings/wattvision.src/wattvision.groovy
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

metadata {

	definition(name: "RainforestEagle", namespace: "augoisms", author: "Justin Walker") {
		capability "Power Meter"
		capability "Refresh"
		capability "Sensor"
        
        attribute "lastUpdated", "String"
	}

	tiles {

		valueTile("power", "device.power", width: 2, height: 2) {
			state "default", label: '${currentValue} W'
		}

		standardTile("refresh", "device.power", inactiveLabel: true, decoration: "flat") {
			state "default", label: '', action: "refresh.refresh", icon: "st.secondary.refresh"
		}
        
        valueTile("lastUpdated", "device.lastUpdated", width: 1, height: 1, decoration: "flat") {
        	state "default", label:'${currentValue}'
	    }

		main "power"
		details(["power", "refresh", "lastUpdated"])

	}
}

def installed() {
	log.debug "RainforestEagle device installed"	
	initialize()
}

def updated() {
	unsubscribe()
	unschedule()
	initialize()
}

def initialize() {
    schedule("0/20 * * * * ? *", refresh)
}

def refreshCallback(response) {
	log.debug "refresh response"
    //log.debug response.headers
    
    log.debug "status: ${response?.status}"
    
    def successCodes = ["200","201","202"]
	boolean success = successCodes.findAll{response?.status?.toString().contains(it)}
    log.debug "success: $success"
    
    if(success) {
    	def json = parseJson(response.body)
		addEagleData(json)
    } else {
    	error()
    }
    
}

def refresh() {
	log.debug "refresh()"
    
    def settings = parent.getSettings(this)
    //log.debug "settings"
    //log.debug settings
    
    def address = settings.theAddr
    def macId = settings.macId
    
    def pre = "${settings.cloudId}:${settings.macId}"
    def encoded = pre.bytes.encodeBase64()
    
    def xmlBody = """<Command>
    <Name>get_instantaneous_demand</Name>
    <MacId>0x${settings.macId}</MacId>
    <Format>JSON</Format>
    </Command>"""

    try {
    
        def hubAction = new hubitat.device.HubAction([
            method: "POST",
            path: "/cgi-bin/post_manager",
            headers: [
                HOST: address,
                "authorization": "Basic $encoded",
                "Content-Type": "application/xml"
            ],
            body: xmlBody],
            device.deviceNetworkId,
            [callback: refreshCallback]
        )
        //log.debug "hubAction"
        //log.trace hubAction
        log.debug "sending request"
        sendHubCommand(hubAction)
    }
    catch (Exception e) {
        log.debug "Hit Exception $e on $hubAction"
    }
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"
}

public addEagleData(json) {

	log.trace "Adding data from Eagle"

    def data = json.InstantaneousDemand
    
    int demand = convertHexToInt(data.Demand)
    int multiplier = convertHexToInt(data.Multiplier)
    int divisor = convertHexToInt(data.Divisor)
    
    // we're not using the devisor bc I can't figure out how
    // to get the tiles to now round the decimal places
    //def value = (demand * multiplier) / divisor
    def value = (demand * multiplier)
    
    def valueString = String.valueOf(value)
    
    sendPowerEvent(new Date(), valueString, 'W', true)    
    
    // update time
	def timeData = [
    	date: new Date(),
        value: new Date().format("yyyy-MM-dd h:mm", location.timeZone),
        name: "lastUpdated"//,
        //isStateChange: true
    ]
    sendEvent(timeData)

}

public error() {
	// there was an error retrieving data
    // clear out the value
    sendPowerEvent(new Date(), '---', 'W', true)    
}

private sendPowerEvent(time, value, units, isLatest = false) {

	def eventData = [
		date           : time,
		value          : value,
		name           : "power",
		displayed      : isLatest,
		//isStateChange  : isLatest,
		description    : "${value} ${units}",
		descriptionText: "${value} ${units}"
	]

	log.debug "sending event: ${eventData}"
	sendEvent(eventData)
}

def parseJson(String s) {
	new groovy.json.JsonSlurper().parseText(s)
}

private Integer convertHexToInt(hex) {
    return new BigInteger(hex[2..-1], 16)
}